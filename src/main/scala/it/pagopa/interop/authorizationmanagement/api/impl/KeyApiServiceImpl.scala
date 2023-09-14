package it.pagopa.interop.authorizationmanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.data.ValidatedNel
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.api.KeyApiService
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiResponseHandlers._
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey
import it.pagopa.interop.authorizationmanagement.model.persistence.KeyAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence.{PersistenceTypes => PersistType}
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

final case class KeyApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  dateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends KeyApiService
    with Validation {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private implicit val settings: ClusterShardingSettings = shardingSettings(entity, system)
  private implicit val implicitSharding: ClusterSharding = sharding

  override def createKeys(clientId: String, keysSeed: Seq[KeySeed])(implicit
    toEntityMarshallerKeys: ToEntityMarshaller[Keys],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Creating keys for client $clientId"
    logger.info(operationLabel)

    val validatedPayload: ValidatedNel[InvalidKey, Seq[ValidKey]] = validateKeys(keysSeed)

    val result: Future[Keys] = for {
      validPayload   <- validatedPayload.toEither.leftMap(err => InvalidKeys(err.toList)).toFuture
      validKeys      <- validateWithCurrentKeys(validPayload, keysIdentifiers).toFuture
      persistentKeys <- validKeys.traverse(PersistentKey.toPersistentKey).toFuture
      addedKeys      <- commander(clientId).askWithStatus(ref => AddKeys(clientId, persistentKeys, ref))
      apiKeys        <- addedKeys.traverse(_.toApi).toFuture
    } yield Keys(apiKeys)

    onComplete(result) { createKeysResponse[Keys](operationLabel)(createKeys200) }
  }

  private def keysIdentifiers: LazyList[PersistType.Kid] = {
    val sliceSize                                 = 1000
    val commanders: Seq[EntityRef[Command]]       = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
    )
    val keyIdentifiers: LazyList[PersistType.Kid] = commanders.to(LazyList).flatMap(ref => slices(ref, sliceSize))

    keyIdentifiers
  }

  private def slices(commander: EntityRef[Command], sliceSize: Int): LazyList[PersistType.Kid] = {
    @tailrec
    def readSlice(
      commander: EntityRef[Command],
      from: Int,
      to: Int,
      lazyList: LazyList[PersistType.Kid]
    ): LazyList[PersistType.Kid] = {
      lazy val slice: Seq[PersistType.Kid] =
        Await.result(commander.ask(ref => ListKid(from, to, ref)), Duration.Inf).getValue
      if (slice.isEmpty)
        lazyList
      else
        readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  override def getClientKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerClientKey: ToEntityMarshaller[Key],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Getting key $keyId for client $clientId"
    logger.info(operationLabel)

    val result: Future[Key] = for {
      persistentKey <- commander(clientId).askWithStatus(ref => GetKey(clientId, keyId, ref))
      apiKey        <- persistentKey.toApi.toFuture
    } yield apiKey

    onComplete(result) { getClientKeyByIdResponse[Key](operationLabel)(getClientKeyById200) }
  }

  override def getClientKeys(clientId: String)(implicit
    toEntityMarshallerKeysCreatedResponse: ToEntityMarshaller[Keys],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Getting keys for client $clientId"
    logger.info(operationLabel)

    val result: Future[Keys] = for {
      persistentKeys <- commander(clientId).askWithStatus(ref => GetKeys(clientId, ref))
      apiKeys        <- persistentKeys.traverse(_.toApi.toFuture)
    } yield Keys(apiKeys)

    onComplete(result) { getClientKeysResponse[Keys](operationLabel)(getClientKeys200) }
  }

  override def deleteClientKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Deleting key $keyId of client $clientId"
    logger.info(operationLabel)

    val result: Future[Done] = commander(clientId).askWithStatus(ref => DeleteKey(clientId, keyId, ref))

    onComplete(result) { deleteClientKeyByIdResponse[Done](operationLabel)(_ => deleteClientKeyById204) }
  }

}
