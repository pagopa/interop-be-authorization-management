package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.api.ClientApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.error.OperatorNotFoundError
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.ClientNotFoundError
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed, OperatorSeed, Problem}
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

  /** Code: 201, Message: Client created, DataType: Client
    * Code: 400, Message: Missing Required Information, DataType: Problem
    */
  override def createClient(clientSeed: ClientSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = {

    logger.info(s"Creating client for agreement ${clientSeed.agreementId}...")

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
            s"Error creating client for agreement ${clientSeed.agreementId}"
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
            getClient500(Problem(Option(ex.getMessage), 500, s"Error while retrieving client $clientId"))
        }
      // This should never occur, but with this check the pattern matching is exhaustive
      case unknownReply =>
        getClient500(Problem(Option(unknownReply.toString()), 500, s"Error while retrieving client $clientId"))
    }
  }

  /** Code: 201, Message: Client list retrieved, DataType: Seq[Client]
    * Code: 400, Message: Missing Required Information, DataType: Problem
    * Code: 500, Message: Missing Required Information, DataType: Problem
    */
  override def listClients(offset: Int, limit: Int, agreementId: Option[String], operatorId: Option[String])(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]]
  ): Route = {
    val sliceSize = 1000

    // This could be implemented using 'anyOf' function of OpenApi, but the generetor does not support it yet
    // see https://github.com/OpenAPITools/openapi-generator/issues/634
    (agreementId, operatorId) match {
      case (None, None) =>
        listClients400(
          Problem(
            Some("At least one parameter is required [ agreementId, operatorId ]"),
            status = 400,
            s"Error retrieving clients list for parameters"
          )
        )
      case (agrId, opId) =>
        val commanders: Seq[EntityRef[Command]] =
          (0 until settings.numberOfShards).map(shard =>
            sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
          )
        val clients: Seq[Client] = commanders.flatMap(ref => slices(ref, sliceSize, agrId, opId).map(_.toApi))
        val paginatedClients     = clients.sortBy(_.id).slice(offset, offset + limit)
        listClients200(paginatedClients)
    }
  }

  private def slices(
    commander: EntityRef[Command],
    sliceSize: Int,
    agreementId: Option[String],
    operatorId: Option[String]
  ): LazyList[PersistentClient] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      from: Int,
      to: Int,
      lazyList: LazyList[PersistentClient]
    ): LazyList[PersistentClient] = {
      lazy val slice: Seq[PersistentClient] =
        Await.result(commander.ask(ref => ListClients(from, to, agreementId, operatorId, ref)), Duration.Inf).getValue
      if (slice.isEmpty)
        lazyList
      else
        readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  /** Code: 201, Message: Operator added, DataType: Client
    * Code: 400, Message: Missing Required Information, DataType: Problem
    * Code: 404, Message: Missing Required Information, DataType: Problem
    */
  override def addOperator(clientId: String, operatorSeed: OperatorSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = {
    logger.info(s"Adding operator ${operatorSeed.operatorId} to client $clientId...")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[PersistentClient]] =
      commander.ask(ref => AddOperator(clientId, operatorSeed.operatorId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => addOperator201(statusReply.getValue.toApi)
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            addOperator404(Problem(Option(ex.getMessage), status = 404, s"Error adding operator to client"))
          case ex =>
            addOperator500(
              Problem(
                Option(ex.getMessage),
                status = 500,
                s"Error adding operator ${operatorSeed.operatorId.toString} to client $clientId"
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
            addOperator500(Problem(Option(ex.getMessage), status = 500, s"Error deleting client $clientId"))
        }
    }
  }

  /** Code: 204, Message: Operator removed
    * Code: 404, Message: Client or operator not found, DataType: Problem
    * Code: 500, Message: Internal server error, DataType: Problem
    */
  override def removeClientOperator(clientId: String, operatorId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Removing operator $operatorId from client $clientId...")

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Done]] =
      commander.ask(ref => RemoveOperator(clientId, operatorId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => removeClientOperator204
      case statusReply if statusReply.isError =>
        statusReply.getError match {
          case ex: ClientNotFoundError =>
            removeClientOperator404(
              Problem(Option(ex.getMessage), status = 404, s"Error removing operator from client")
            )
          case ex: OperatorNotFoundError =>
            removeClientOperator404(
              Problem(Option(ex.getMessage), status = 404, s"Error removing operator from client")
            )
          case ex =>
            removeClientOperator500(
              Problem(Option(ex.getMessage), status = 500, s"Error removing operator $operatorId from client $clientId")
            )
        }
    }
  }
}
