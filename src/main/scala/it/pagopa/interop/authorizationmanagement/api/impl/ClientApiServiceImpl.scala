package it.pagopa.interop.authorizationmanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.authorizationmanagement.api.ClientApiService
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.{PersistentClient, PersistentClientKind}
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.jwt.{ADMIN_ROLE, INTERNAL_ROLE, M2M_ROLE, SECURITY_ROLE}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientAdapters._

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

final case class ClientApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
) extends ClientApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val settings: ClusterShardingSettings = shardingSettings(entity, system)

  def internalServerError(responseProblem: Problem)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route =
    complete((500, responseProblem))

  /** Code: 201, Message: Client created, DataType: Client
    * Code: 400, Message: Missing Required Information, DataType: Problem
    */
  override def createClient(clientSeed: ClientSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Creating client for Consumer {}", clientSeed.consumerId)

    val clientId = uuidSupplier.get

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId.toString, settings.numberOfShards))

    val persistentClient                              = PersistentClient.toPersistentClient(clientId, clientSeed)
    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddClient(persistentClient, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        statusReply.getValue.toApi.fold(
          ex => internalServerError(problemOf(StatusCodes.InternalServerError, GenericError(ex.getMessage))),
          client => createClient201(client)
        )
      case Success(statusReply)                          =>
        logger.error(s"Error while creating client for Consumer ${clientSeed.consumerId}", statusReply.getError)
        createClient409(problemOf(StatusCodes.Conflict, ClientAlreadyExisting))
      case Failure(ex)                                   =>
        logger.error(s"Error while creating client for Consumer ${clientSeed.consumerId}", ex)
        createClient400(problemOf(StatusCodes.BadRequest, CreateClientError(clientSeed.consumerId.toString)))
    }

  }

  /** Code: 200, Message: Client retrieved, DataType: Client
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def getClient(clientId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, SECURITY_ROLE, M2M_ROLE, INTERNAL_ROLE) {
    logger.info("Retrieving Client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] = commander.ask(ref => GetClient(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.toApi.fold(
          ex => internalServerError(problemOf(StatusCodes.InternalServerError, GenericError(ex.getMessage))),
          client => getClient200(client)
        )
      case statusReply if statusReply.isError   =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            logger.info(s"Error while retrieving Client ${clientId}", ex)
            getClient404(problemOf(StatusCodes.NotFound, ex))
          case ex                      =>
            logger.error(s"Error while retrieving Client ${clientId}", ex)
            internalServerError(problemOf(StatusCodes.InternalServerError, GetClientError(clientId)))
        }
      // This should never occur, but with this check the pattern matching is exhaustive
      case unknownReply                         =>
        logger.error(s"Error while retrieving Client ${clientId} - $unknownReply")
        internalServerError(
          problemOf(StatusCodes.InternalServerError, GetClientServerError(clientId, unknownReply.toString))
        )
    }
  }

  /** Code: 200, Message: Client list retrieved, DataType: Seq[Client]
    * Code: 400, Message: Missing Required Information, DataType: Problem
    */
  override def listClients(
    offset: Int,
    limit: Int,
    relationshipId: Option[String],
    consumerId: Option[String],
    purposeId: Option[String],
    kind: Option[String]
  )(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, SECURITY_ROLE, M2M_ROLE) {
    logger.info(
      "Listing clients for relationship {} and consumer {}, purpose {} and kind {}",
      relationshipId,
      consumerId,
      purposeId,
      kind
    )

    val sliceSize = 1000

    val kindEnum: Option[PersistentClientKind]  =
      kind.flatMap(ClientKind.fromValue(_).toOption).map(PersistentClientKind.fromApi)
    val commanders: Seq[EntityRef[Command]]     =
      (0 until settings.numberOfShards).map(shard =>
        sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
      )
    val persistentClient: Seq[PersistentClient] =
      commanders.flatMap(ref => slices(ref, sliceSize, relationshipId, consumerId, purposeId, kindEnum))
    val clients: Either[Throwable, Seq[Client]] = persistentClient.traverse(client => client.toApi)
    val paginatedClients                        = clients.map(_.sortBy(_.id).slice(offset, offset + limit))
    paginatedClients.fold(
      ex => internalServerError(problemOf(StatusCodes.InternalServerError, GenericError(ex.getMessage))),
      clients => listClients200(clients)
    )
  }

  private def slices(
    commander: EntityRef[Command],
    sliceSize: Int,
    relationshipId: Option[String],
    consumerId: Option[String],
    purposeId: Option[String],
    kind: Option[PersistentClientKind]
  ): LazyList[PersistentClient] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      from: Int,
      to: Int,
      lazyList: LazyList[PersistentClient]
    ): LazyList[PersistentClient] = {
      lazy val slice: Seq[PersistentClient] =
        Await
          .result(
            commander.ask(ref => ListClients(from, to, relationshipId, consumerId, purposeId, kind, ref)),
            Duration.Inf
          )
          .getValue
      if (slice.isEmpty)
        lazyList
      else
        readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  /** Code: 201, Message: Party Relationship added, DataType: Client
    * Code: 400, Message: Missing Required Information, DataType: Problem
    * Code: 404, Message: Missing Required Information, DataType: Problem
    */
  override def addRelationship(clientId: String, relationshipSeed: PartyRelationshipSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Adding relationship {} to client {}", relationshipSeed.relationshipId, clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddRelationship(clientId, relationshipSeed.relationshipId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.toApi.fold(
          ex => internalServerError(problemOf(StatusCodes.InternalServerError, GenericError(ex.getMessage))),
          client => addRelationship201(client)
        )
      case statusReply                          =>
        logger.error(
          s"Error while adding relationship ${relationshipSeed.relationshipId} to client ${clientId}",
          statusReply.getError
        )
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            addRelationship404(problemOf(StatusCodes.NotFound, ex))
          case _                       =>
            internalServerError(
              problemOf(
                StatusCodes.InternalServerError,
                AddRelationshipError(relationshipSeed.relationshipId.toString, clientId)
              )
            )
        }
    }

  }

  /** Code: 204, Message: Client deleted
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def deleteClient(
    clientId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route =
    authorize(ADMIN_ROLE) {
      logger.info("Deleting client {}", clientId)

      val commander: EntityRef[Command] =
        sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

      val result: Future[StatusReply[Done]] =
        commander.ask(ref => DeleteClient(clientId, ref))

      onSuccess(result) {
        case statusReply if statusReply.isSuccess => deleteClient204
        case statusReply                          =>
          logger.error(s"Error while deleting client ${clientId}", statusReply.getError)
          statusReply.getError match {
            case ex: ClientNotFoundError =>
              deleteClient404(problemOf(StatusCodes.NotFound, ex))
            case _                       =>
              internalServerError(problemOf(StatusCodes.InternalServerError, DeleteClientError(clientId)))
          }
      }
    }

  /** Code: 204, Message: Party Relationship removed
    * Code: 404, Message: Client or Party Relationship not found, DataType: Problem
    * Code: 500, Message: Internal server error, DataType: Problem
    */
  override def removeClientRelationship(clientId: String, relationshipId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE) {
    logger.info("Removing relationship {} from client {}", relationshipId, clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Done]] =
      commander.ask(ref => RemoveRelationship(clientId, relationshipId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => removeClientRelationship204
      case statusReply                          =>
        logger.error(
          s"Error while removing relationship ${relationshipId} from client ${clientId}",
          statusReply.getError
        )
        statusReply.getError match {
          case ex: ClientNotFoundError            =>
            removeClientRelationship404(problemOf(StatusCodes.NotFound, ex))
          case ex: PartyRelationshipNotFoundError =>
            removeClientRelationship404(problemOf(StatusCodes.NotFound, ex))
          case _                                  =>
            internalServerError(
              problemOf(StatusCodes.InternalServerError, RemoveRelationshipError(relationshipId, clientId))
            )
        }
    }
  }

  /** Code: 200, Message: Client retrieved, DataType: Client
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def getClientByPurposeId(clientId: String, purposeId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = authorize(ADMIN_ROLE, SECURITY_ROLE, M2M_ROLE) {
    logger.info("Retrieving Client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => GetClientByPurpose(clientId, purposeId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        statusReply.getValue.toApi.fold(
          ex => internalServerError(problemOf(StatusCodes.InternalServerError, GenericError(ex.getMessage))),
          client => getClientByPurposeId200(client)
        )
      case statusReply if statusReply.isError   =>
        logger.info(s"Error while retrieving Client for client=$clientId/purpose=$purposeId", statusReply.getError)
        statusReply.getError match {
          case ex: ClientWithPurposeNotFoundError =>
            getClientByPurposeId404(problemOf(StatusCodes.NotFound, ex))
          case _                                  =>
            internalServerError(problemOf(StatusCodes.InternalServerError, GetClientError(clientId)))
        }
      case unknownReply                         =>
        logger.error(s"Error while retrieving Client for client=$clientId/purpose=$purposeId, - Internal server error")
        internalServerError(
          problemOf(StatusCodes.InternalServerError, GetClientServerError(clientId, unknownReply.toString))
        )
    }
  }
}
