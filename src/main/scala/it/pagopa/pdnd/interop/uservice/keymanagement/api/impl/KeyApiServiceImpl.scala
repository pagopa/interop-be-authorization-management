package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import it.pagopa.pdnd.interop.uservice.keymanagement.api.KeyApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse, Problem}
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Recursion"
  )
)
class KeyApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
) extends KeyApiService
    with Validation {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  @inline private def getShard(id: String): String = Math.abs((id.hashCode % settings.numberOfShards)).toString

  /** Code: 201, Message: Keys created
    * Code: 400, Message: Missing Required Information
    * Code: 404, Message: Client id not found, DataType: Problem
    */
  override def createKeys(clientId: String, key: Seq[String])(implicit
    toEntityMarshallerKeysResponse: ToEntityMarshaller[KeysResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Creating keys for client ${clientId}...")

    val validatedPayload: ValidatedNel[String, Seq[ValidKey]] = validateKeys(key)

    validatedPayload.andThen(k => validateWithCurrentKeys(k, keysIdentifiers)) match {
      case Valid(validKeys) =>
        val commander: EntityRef[Command] = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))
        val result: Future[StatusReply[KeysResponse]] =
          commander.ask(ref => AddKeys(clientId, validKeys, ref))
        onSuccess(result) {
          case statusReply if statusReply.isSuccess => createKeys201(statusReply.getValue)
          case statusReply if statusReply.isError =>
            createKeys400(Problem(Option(statusReply.getError.getMessage), status = 400, "some error"))
        }

      case Invalid(errors) => createKeys400(Problem(Option(errors.toList.mkString(", ")), status = 400, "some error"))
    }
  }

  /** Code: 200, Message: List of keyIdentifiers, DataType: Seq[Pet]
    */
  private def keysIdentifiers: LazyList[Kid] = {
    val sliceSize = 1000
    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
    )
    val keyIdentifiers: LazyList[Kid] = commanders.to(LazyList).flatMap(ref => slices(ref, sliceSize))

    keyIdentifiers
  }

  private def slices(commander: EntityRef[Command], sliceSize: Int): LazyList[Kid] = {
    @tailrec
    def readSlice(commander: EntityRef[Command], from: Int, to: Int, lazyList: LazyList[Kid]): LazyList[Kid] = {
      lazy val slice: Seq[Kid] =
        Await.result(commander.ask(ref => ListKid(from, to, ref)), Duration.Inf).getValue
      if (slice.isEmpty)
        lazyList
      else
        readSlice(commander, to, to + sliceSize, slice.to(LazyList) #::: lazyList)
    }
    readSlice(commander, 0, sliceSize, LazyList.empty)
  }

  /** Code: 200, Message: returns the corresponding key, DataType: Key
    * Code: 404, Message: Key not found, DataType: Problem
    */
  override def getClientKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerKey: ToEntityMarshaller[Key],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Getting key ${keyId} for client ${clientId}...")
    val commander: EntityRef[Command] = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))

    val result: Future[StatusReply[Key]] = commander.ask(ref => GetKey(clientId, keyId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getClientKeyById200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        getClientKeyById404(Problem(Option(statusReply.getError.getMessage), status = 404, "some error"))
    }
  }

  /** Code: 200, Message: returns the corresponding array of keys, DataType: KeysCreatedResponse
    * Code: 404, Message: Client id not found, DataType: Problem
    */
  override def getClientKeys(clientId: String)(implicit
    toEntityMarshallerKeysCreatedResponse: ToEntityMarshaller[KeysResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Getting keys for client ${clientId}...")
    val commander: EntityRef[Command] = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))

    val result: Future[StatusReply[KeysResponse]] = commander.ask(ref => GetKeys(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getClientKeys200(statusReply.getValue)
      case statusReply if statusReply.isError =>
        getClientKeys404(Problem(Option(statusReply.getError.getMessage), status = 404, "some error"))
    }
  }

  /** Code: 204, Message: the corresponding key has been deleted.
    * Code: 404, Message: Key not found, DataType: Problem
    */
  override def deleteClientKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Delete key $keyId belonging to $clientId...")
    val commander: EntityRef[Command]     = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))
    val result: Future[StatusReply[Done]] = commander.ask(ref => DeleteKey(clientId, keyId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => deleteClientKeyById204
      case statusReply if statusReply.isError =>
        deleteClientKeyById404(Problem(Option(statusReply.getError.getMessage), status = 400, "some error"))
    }
  }

  /** Code: 204, Message: the corresponding key has been disabled.
    * Code: 404, Message: Key not found, DataType: Problem
    */
  override def disableKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Disabling key $keyId belonging to $clientId...")
    val commander: EntityRef[Command]     = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))
    val result: Future[StatusReply[Done]] = commander.ask(ref => DisableKey(clientId, keyId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => disableKeyById204
      case statusReply if statusReply.isError =>
        disableKeyById404(Problem(Option(statusReply.getError.getMessage), status = 400, "some error"))
    }
  }

  /** Code: 204, Message: the corresponding key has been enabled.
    * Code: 404, Message: Key not found, DataType: Problem
    */
  override def enableKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Enabling key $keyId belonging to $clientId...")
    val commander: EntityRef[Command]     = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))
    val result: Future[StatusReply[Done]] = commander.ask(ref => EnableKey(clientId, keyId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => enableKeyById204
      case statusReply if statusReply.isError =>
        enableKeyById404(Problem(Option(statusReply.getError.getMessage), status = 400, "some error"))
    }
  }
}
