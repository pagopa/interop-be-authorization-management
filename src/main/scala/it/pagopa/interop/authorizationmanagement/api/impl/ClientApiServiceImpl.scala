package it.pagopa.interop.authorizationmanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.api.ClientApiService
import it.pagopa.interop.authorizationmanagement.api.impl.ClientApiResponseHandlers._
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.{PersistentClient, PersistentClientKind}
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import cats.syntax.all._

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final case class ClientApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends ClientApiService
    with Validation {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private implicit val settings: ClusterShardingSettings = shardingSettings(entity, system)
  private implicit val implicitSharding: ClusterSharding = sharding

  override def createClient(clientSeed: ClientSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Creating client for Consumer ${clientSeed.consumerId}"
    logger.info(operationLabel)

    val clientId = uuidSupplier.get()

    val persistentClient       = PersistentClient.toPersistentClient(clientId, clientSeed)
    val result: Future[Client] =
      commander(clientId.toString)
        .askWithStatus(ref => AddClient(persistentClient, ref))
        .map(_.toApi)

    onComplete(result) { createClientResponse(operationLabel)(createClient200) }
  }

  override def getClient(clientId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Retrieving Client $clientId"
    logger.info(operationLabel)

    val result: Future[Client] =
      commander(clientId)
        .askWithStatus(ref => GetClient(clientId, ref))
        .map(_.toApi)

    onComplete(result) { getClientResponse[Client](operationLabel)(getClient200) }
  }

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
  ): Route = {
    val operationLabel: String =
      s"Listing clients for relationship $relationshipId and consumer $consumerId, purpose $purposeId and kind $kind"
    logger.info(operationLabel)

    val sliceSize = 1000

    val kindEnum: Option[PersistentClientKind] =
      kind.flatMap(ClientKind.fromValue(_).toOption).map(PersistentClientKind.fromApi)
    val commanders: Seq[EntityRef[Command]]    =
      (0 until settings.numberOfShards).map(shard =>
        sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
      )

    val sliced: EntityRef[Command] => Try[LazyList[PersistentClient]] =
      ref => slices(ref, sliceSize, relationshipId, consumerId, purposeId, kindEnum)
    val persistentClient                                              = commanders.map(sliced).sequence.map(_.flatten)

    val paginatedClients: Try[Seq[Client]] =
      persistentClient.map(_.map(_.toApi).sortBy(_.id).slice(offset, offset + limit))

    listClientsResponse[Seq[Client]](operationLabel)(listClients200)(paginatedClients)
  }

  private def slices(
    commander: EntityRef[Command],
    sliceSize: Int,
    relationshipId: Option[String],
    consumerId: Option[String],
    purposeId: Option[String],
    kind: Option[PersistentClientKind]
  ): Try[LazyList[PersistentClient]] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      from: Int,
      to: Int,
      lazyList: LazyList[PersistentClient]
    ): Try[LazyList[PersistentClient]] = {
      lazy val slice: Try[Seq[PersistentClient]] =
        Try(
          Await
            .result(
              commander.ask(ref => ListClients(from, to, relationshipId, consumerId, purposeId, kind, ref)),
              Duration.Inf
            )
            .getValue
        )
      slice match {
        case Success(s) if s.isEmpty => Success(lazyList)
        case Success(s)              => readSlice(commander, to, to + sliceSize, s.to(LazyList) #::: lazyList)
        case Failure(ex)             => Failure(ex)
      }

    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  override def addRelationship(clientId: String, relationshipSeed: PartyRelationshipSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Adding relationship ${relationshipSeed.relationshipId} to client $clientId"
    logger.info(operationLabel)

    val result: Future[Client] =
      commander(clientId)
        .askWithStatus(ref => AddRelationship(clientId, relationshipSeed.relationshipId, ref))
        .map(_.toApi)

    onComplete(result) { addRelationshipResponse[Client](operationLabel)(addRelationship200) }

  }

  override def deleteClient(
    clientId: String
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    val operationLabel: String = s"Deleting client $clientId"
    logger.info(operationLabel)

    val result: Future[Done] = commander(clientId).askWithStatus(ref => DeleteClient(clientId, ref))

    onComplete(result) { deleteClientResponse[Done](operationLabel)(_ => deleteClient204) }
  }

  override def removeClientRelationship(clientId: String, relationshipId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Removing relationship $relationshipId from client $clientId"
    logger.info(operationLabel)

    val result: Future[Done] =
      commander(clientId).askWithStatus(ref => RemoveRelationship(clientId, relationshipId, ref))

    onComplete(result) { removeClientRelationshipResponse[Done](operationLabel)(_ => removeClientRelationship204) }
  }

  override def getClientByPurposeId(clientId: String, purposeId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Retrieving Client $clientId"
    logger.info(operationLabel)

    val result: Future[Client] =
      commander(clientId)
        .askWithStatus(ref => GetClientByPurpose(clientId, purposeId, ref))
        .map(_.toApi)

    onComplete(result) { getClientByPurposeIdResponse[Client](operationLabel)(getClientByPurposeId200) }
  }
}
