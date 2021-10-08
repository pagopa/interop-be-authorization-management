package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.{complete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
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
import it.pagopa.pdnd.interop.uservice.keymanagement.service.UUIDSupplier
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.StringPlusAny",
    "org.wartremover.warts.Recursion"
  )
)
class ClientApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
) extends ClientApiService
    with Validation {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

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
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = {

    logger.info(s"Creating client for E-Service ${clientSeed.eServiceId}...")

    val clientId = uuidSupplier.get

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId.toString, settings.numberOfShards))

    val persistentClient = PersistentClient.toPersistentClient(clientId, clientSeed)
    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddClient(persistentClient, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => createClient201(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        createClient400(
          Problem(
            Option(statusReply.getError.getMessage),
            status = 400,
            s"Error creating client for E-Service ${clientSeed.eServiceId}"
          )
        )
    }

  }

  /** Code: 200, Message: Client retrieved, DataType: Client
    * Code: 404, Message: Client not found, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def getClient(clientId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = {
    logger.info(s"Retrieving Client $clientId...")
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] = commander.ask(ref => GetClient(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        getClient200(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            getClient404(Problem(Option(ex.getMessage), status = 404, s"Error on client retrieve"))
          case ex =>
            internalServerError(Problem(Option(ex.getMessage), 500, s"Error while retrieving client $clientId"))
        }
      // This should never occur, but with this check the pattern matching is exhaustive
      case unknownReply =>
        internalServerError(Problem(Option(unknownReply.toString()), 500, s"Error while retrieving client $clientId"))
    }
  }

  /** Code: 201, Message: Client list retrieved, DataType: Seq[Client]
    * Code: 400, Message: Missing Required Information, DataType: Problem
    * Code: 500, Message: Missing Required Information, DataType: Problem
    */
  override def listClients(
    offset: Int,
    limit: Int,
    eServiceId: Option[String],
    relationshipId: Option[String],
    consumerId: Option[String]
  )(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]]
  ): Route = {
    val sliceSize = 1000

    // This could be implemented using 'anyOf' function of OpenApi, but the generator does not support it yet
    // see https://github.com/OpenAPITools/openapi-generator/issues/634
    (eServiceId, relationshipId, consumerId) match {
      case (None, None, None) =>
        listClients400(
          Problem(
            Some("At least one parameter is required [ eServiceId, relationshipId, consumerId ]"),
            status = 400,
            s"Error retrieving clients list for parameters"
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
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = {
    logger.info(s"Adding relationship ${relationshipSeed.relationshipId} to client $clientId...")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddRelationship(clientId, relationshipSeed.relationshipId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => addRelationship201(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            addRelationship404(Problem(Option(ex.getMessage), status = 404, s"Error adding relationship to client"))
          case ex =>
            internalServerError(
              Problem(
                Option(ex.getMessage),
                status = 500,
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
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    logger.info(s"Deleting client $clientId...")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Done]] =
      commander.ask(ref => DeleteClient(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => deleteClient204
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            deleteClient404(Problem(Option(ex.getMessage), status = 404, s"Error deleting client"))
          case ex =>
            internalServerError(Problem(Option(ex.getMessage), status = 500, s"Error deleting client $clientId"))
        }
    }
  }

  /** Code: 204, Message: Party Relationship removed
    * Code: 404, Message: Client or Party Relationship not found, DataType: Problem
    * Code: 500, Message: Internal server error, DataType: Problem
    */
  override def removeClientRelationship(clientId: String, relationshipId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Removing relationship $relationshipId from client $clientId...")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Done]] =
      commander.ask(ref => RemoveRelationship(clientId, relationshipId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => removeClientRelationship204
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            removeClientRelationship404(
              Problem(Option(ex.getMessage), status = 404, s"Error removing relationship from client")
            )
          case ex: PartyRelationshipNotFoundError =>
            removeClientRelationship404(
              Problem(Option(ex.getMessage), status = 404, s"Error removing relationship from client")
            )
          case ex =>
            internalServerError(
              Problem(
                Option(ex.getMessage),
                status = 500,
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
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    logger.info(s"Activating client $clientId...")
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))
    val result: Future[StatusReply[Done]] = commander.ask(ref => ActivateClient(clientId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => activateClientById204
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case err: ClientNotFoundError =>
            activateClientById404(Problem(Some(err.getMessage), status = 404, "Not found"))
          case err: ClientAlreadyActiveError =>
            activateClientById400(Problem(Some(err.getMessage), status = 400, "Bad Request"))
          case err =>
            complete((500, Problem(Option(err.getMessage), status = 500, "Unexpected error")))
        }

    }
  }

  /** Code: 204, Message: the corresponding client has been suspended.
    * Code: 404, Message: Client not found, DataType: Problem
    */
  override def suspendClientById(
    clientId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route = {
    logger.info(s"Suspending client $clientId...")
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))
    val result: Future[StatusReply[Done]] = commander.ask(ref => SuspendClient(clientId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => suspendClientById204
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case err: ClientNotFoundError =>
            suspendClientById404(Problem(Some(err.getMessage), status = 404, "Not found"))
          case err: ClientAlreadySuspendedError =>
            suspendClientById400(Problem(Some(err.getMessage), status = 400, "Bad Request"))
          case err =>
            complete((500, Problem(Option(err.getMessage), status = 500, "Unexpected error")))
        }

    }
  }
}
