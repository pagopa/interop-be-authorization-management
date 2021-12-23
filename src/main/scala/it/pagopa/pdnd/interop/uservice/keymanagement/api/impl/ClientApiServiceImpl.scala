package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.api.ClientApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.error.PartyRelationshipNotFoundError
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.{
  ClientAlreadyActiveError,
  ClientAlreadySuspendedError,
  ClientNotFoundError
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed, PartyRelationshipSeed, Problem}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class ClientApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
) extends ClientApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

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
  ): Route = {
    logger.info("Creating client for E-Service {}", clientSeed.eServiceId)

    val clientId = uuidSupplier.get

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId.toString, settings.numberOfShards))

    val persistentClient = PersistentClient.toPersistentClient(clientId, clientSeed)
    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddClient(persistentClient, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess => createClient201(statusReply.getValue.toApi)
      case Success(statusReply) =>
        logger.error("Error while creating client for E-Service {}", clientSeed.eServiceId, statusReply.getError)
        createClient409(
          problemOf(
            StatusCodes.Conflict,
            "0024",
            statusReply.getError,
            s"Error creating client for E-Service ${clientSeed.eServiceId}"
          )
        )
      case Failure(ex) =>
        logger.error("Error while creating client for E-Service {}", clientSeed.eServiceId, ex)
        createClient400(
          problemOf(StatusCodes.BadRequest, "0001", ex, s"Error creating client for E-Service ${clientSeed.eServiceId}")
        )
    }

  }

  /** Code: 200, Message: Client retrieved, DataType: Client
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def getClient(clientId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Retrieving Client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] = commander.ask(ref => GetClient(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        getClient200(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            logger.error("Error while retrieving Client {}", clientId, ex)
            getClient404(problemOf(StatusCodes.NotFound, "0002", ex, "Error on client retrieve"))
          case ex =>
            logger.error("Error while retrieving Client {}", clientId, ex)
            internalServerError(
              problemOf(StatusCodes.InternalServerError, "0003", ex, s"Error while retrieving client $clientId")
            )
        }
      // This should never occur, but with this check the pattern matching is exhaustive
      case unknownReply =>
        logger.error("Error while retrieving Client {} - Internal server error", clientId)
        internalServerError(
          problemOf(
            StatusCodes.InternalServerError,
            "0004",
            defaultMessage = s"Error while retrieving client $clientId : ${unknownReply.toString}"
          )
        )
    }
  }

  /** Code: 200, Message: Client list retrieved, DataType: Seq[Client]
    * Code: 400, Message: Missing Required Information, DataType: Problem
    */
  override def listClients(
    offset: Int,
    limit: Int,
    eServiceId: Option[String],
    relationshipId: Option[String],
    consumerId: Option[String]
  )(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info(
      "Listing clients for e-service {} on relationship {} for consumer {}",
      eServiceId,
      relationshipId,
      consumerId
    )

    val sliceSize = 1000

    // This could be implemented using 'anyOf' function of OpenApi, but the generator does not support it yet
    // see https://github.com/OpenAPITools/openapi-generator/issues/634
    (eServiceId, relationshipId, consumerId) match {
      case (None, None, None) =>
        logger.error("Error listing clients: no required parameters have been provided")
        listClients400(
          problemOf(
            StatusCodes.BadRequest,
            "0005",
            defaultMessage = "At least one parameter is required [ eServiceId, relationshipId, consumerId ]"
          )
        )
      case (agrId, opId, conId) =>
        val commanders: Seq[EntityRef[Command]] =
          (0 until settings.numberOfShards).map(shard =>
            sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
          )
        val clients: Seq[Client] = commanders.flatMap(ref => slices(ref, sliceSize, agrId, opId, conId).map(_.toApi))
        val paginatedClients     = clients.sortBy(_.id).slice(offset, offset + limit)
        listClients200(paginatedClients)
    }
  }

  private def slices(
    commander: EntityRef[Command],
    sliceSize: Int,
    eServiceId: Option[String],
    relationshipId: Option[String],
    consumerId: Option[String]
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
            commander.ask(ref => ListClients(from, to, eServiceId, relationshipId, consumerId, ref)),
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
  ): Route = {
    logger.info("Adding relationship {} to client {}", relationshipSeed.relationshipId, clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddRelationship(clientId, relationshipSeed.relationshipId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => addRelationship201(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        logger.error(
          "Error while adding relationship {} to client {}",
          relationshipSeed.relationshipId,
          clientId,
          statusReply.getError
        )
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            addRelationship404(problemOf(StatusCodes.NotFound, "0006", ex, "Error adding relationship to client"))
          case ex =>
            internalServerError(
              problemOf(
                StatusCodes.InternalServerError,
                "0007",
                ex,
                s"Error adding relationship ${relationshipSeed.relationshipId.toString} to client $clientId"
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
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info("Deleting client {}", clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Done]] =
      commander.ask(ref => DeleteClient(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => deleteClient204
      case statusReply if statusReply.isError =>
        logger.error("Error while deleting client {}", clientId, statusReply.getError)
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            deleteClient404(problemOf(StatusCodes.NotFound, "0008", ex, "Error deleting client"))
          case ex =>
            internalServerError(
              problemOf(StatusCodes.InternalServerError, "0009", ex, s"Error deleting client $clientId")
            )
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
  ): Route = {
    logger.info("Removing relationship {} from client {}", relationshipId, clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Done]] =
      commander.ask(ref => RemoveRelationship(clientId, relationshipId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => removeClientRelationship204
      case statusReply if statusReply.isError =>
        logger.error(
          "Error while removing relationship {} from client {}",
          relationshipId,
          clientId,
          statusReply.getError
        )
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            removeClientRelationship404(
              problemOf(StatusCodes.NotFound, "0010", ex, "Error removing relationship from client")
            )
          case ex: PartyRelationshipNotFoundError =>
            removeClientRelationship404(
              problemOf(StatusCodes.NotFound, "0011", ex, "Error removing relationship from client")
            )
          case ex =>
            internalServerError(
              problemOf(
                StatusCodes.InternalServerError,
                "0012",
                ex,
                s"Error removing relationship $relationshipId from client $clientId"
              )
            )
        }
    }
  }

  /** Code: 204, Message: the client has been activated.
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def activateClientById(
    clientId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info("Activating client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))
    val result: Future[StatusReply[Done]] = commander.ask(ref => ActivateClient(clientId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => activateClientById204
      case statusReply if statusReply.isError =>
        logger.error("Error while activating client {}", clientId, statusReply.getError)
        statusReply.getError match {
          case err: ClientNotFoundError =>
            activateClientById404(problemOf(StatusCodes.NotFound, "0013", err))
          case err: ClientAlreadyActiveError =>
            activateClientById400(problemOf(StatusCodes.BadRequest, "0014", err))
          case err =>
            internalServerError(problemOf(StatusCodes.InternalServerError, "0015", err))
        }

    }
  }

  /** Code: 204, Message: the corresponding client has been suspended.
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def suspendClientById(
    clientId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    logger.info("Suspending client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))
    val result: Future[StatusReply[Done]] = commander.ask(ref => SuspendClient(clientId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => suspendClientById204
      case statusReply if statusReply.isError =>
        logger.error("Error while suspending client {}", clientId, statusReply.getError)
        statusReply.getError match {
          case err: ClientNotFoundError =>
            suspendClientById404(problemOf(StatusCodes.NotFound, "0016", err))
          case err: ClientAlreadySuspendedError =>
            suspendClientById400(problemOf(StatusCodes.BadRequest, "0017", err))
          case err =>
            internalServerError(problemOf(StatusCodes.InternalServerError, "0018", err))
        }

    }
  }
}
