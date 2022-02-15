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
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.api.ClientApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.KeyManagementErrors._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed, PartyRelationshipSeed, Problem}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class ClientApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends ClientApiService
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
    logger.info("Creating client for Consumer {}", clientSeed.consumerId)

    val clientId = uuidSupplier.get

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId.toString, settings.numberOfShards))

    val persistentClient = PersistentClient.toPersistentClient(clientId, clientSeed)
    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddClient(persistentClient, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess => createClient201(statusReply.getValue.toApi)
      case Success(statusReply) =>
        logger.error("Error while creating client for Consumer {}", clientSeed.consumerId, statusReply.getError)
        createClient409(problemOf(StatusCodes.Conflict, ClientAlreadyExisting))
      case Failure(ex) =>
        logger.error("Error while creating client for Consumer {}", clientSeed.consumerId, ex)
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
            getClient404(problemOf(StatusCodes.NotFound, ex))
          case ex =>
            logger.error("Error while retrieving Client {}", clientId, ex)
            internalServerError(problemOf(StatusCodes.InternalServerError, GetClientError(clientId)))
        }
      // This should never occur, but with this check the pattern matching is exhaustive
      case unknownReply =>
        logger.error("Error while retrieving Client {} - Internal server error", clientId)
        internalServerError(
          problemOf(StatusCodes.InternalServerError, GetClientServerError(clientId, unknownReply.toString))
        )
    }
  }

  /** Code: 200, Message: Client list retrieved, DataType: Seq[Client]
    * Code: 400, Message: Missing Required Information, DataType: Problem
    */
  override def listClients(offset: Int, limit: Int, relationshipId: Option[String], consumerId: Option[String])(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Listing clients for relationship {} and consumer {}", relationshipId, consumerId)

    val sliceSize = 1000

    // This could be implemented using 'anyOf' function of OpenApi, but the generator does not support it yet
    // see https://github.com/OpenAPITools/openapi-generator/issues/634
    (relationshipId, consumerId) match {
      case (None, None) =>
        logger.error("Error listing clients: no required parameters have been provided")
        listClients400(problemOf(StatusCodes.BadRequest, ListClientErrors))
      case (relId, conId) =>
        val commanders: Seq[EntityRef[Command]] =
          (0 until settings.numberOfShards).map(shard =>
            sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
          )
        val clients: Seq[Client] = commanders.flatMap(ref => slices(ref, sliceSize, relId, conId).map(_.toApi))
        val paginatedClients     = clients.sortBy(_.id).slice(offset, offset + limit)
        listClients200(paginatedClients)
    }
  }

  private def slices(
    commander: EntityRef[Command],
    sliceSize: Int,
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
          .result(commander.ask(ref => ListClients(from, to, relationshipId, consumerId, ref)), Duration.Inf)
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
            addRelationship404(problemOf(StatusCodes.NotFound, ex))
          case _ =>
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
            deleteClient404(problemOf(StatusCodes.NotFound, ex))
          case _ =>
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
            removeClientRelationship404(problemOf(StatusCodes.NotFound, ex))
          case ex: PartyRelationshipNotFoundError =>
            removeClientRelationship404(problemOf(StatusCodes.NotFound, ex))
          case _ =>
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
  ): Route = {
    logger.info("Retrieving Client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] = for {
      purposeUUID <- purposeId.toFutureUUID
      client      <- commander.ask(ref => GetClientByPurpose(clientId, purposeUUID, ref))
    } yield client

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        getClientByPurposeId200(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientWithPurposeNotFoundError =>
            logger.error(
              s"Error while retrieving Client for client=$clientId/purpose=$purposeId",
              clientId,
              purposeId,
              ex
            )
            getClientByPurposeId404(problemOf(StatusCodes.NotFound, ex))
          case ex =>
            logger.error(
              s"Error while retrieving Client for client=$clientId/purpose=$purposeId",
              clientId,
              purposeId,
              ex
            )
            internalServerError(problemOf(StatusCodes.InternalServerError, GetClientError(clientId)))
        }
      case unknownReply =>
        logger.error(
          s"Error while retrieving Client for client=$clientId/purpose=$purposeId, - Internal server error",
          clientId,
          purposeId
        )
        internalServerError(
          problemOf(StatusCodes.InternalServerError, GetClientServerError(clientId, unknownReply.toString))
        )
    }
  }
}
