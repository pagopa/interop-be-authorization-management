package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.api.ClientApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.errors.ClientNotFoundError
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{
  AddClient,
  Command,
  GetClient,
  KeyPersistentBehavior,
  ListClients
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed, Problem}
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
        val clients: Seq[Client] = commanders.flatMap(ref => sliceClients(ref, offset, limit, agrId, opId))
        listClients201(clients)
    }
  }

  private def sliceClients(
    commander: EntityRef[Command],
    offset: Int,
    limit: Int,
    agreementId: Option[String],
    operatorId: Option[String]
  ): LazyList[Client] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      offset: Int,
      limit: Int,
      agreementId: Option[String],
      operatorId: Option[String],
      lazyList: LazyList[Client]
    ): LazyList[Client] = {
      lazy val slice: Seq[Client] =
        Await
          .result(commander.ask(ref => ListClients(offset, limit, agreementId, operatorId, ref)), Duration.Inf)
          .getValue
          .map(_.toApi)
      if (slice.isEmpty)
        lazyList
      else
        readSlice(commander, offset + limit, limit, agreementId, operatorId, slice.to(LazyList) #::: lazyList)
    }

    readSlice(commander, offset, limit, agreementId, operatorId, LazyList.empty)
  }

}
