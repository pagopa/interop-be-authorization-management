package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

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
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse, Problem}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

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

  @inline private def getShard(id: String): String = (id.hashCode % settings.numberOfShards).toString

  /** Code: 201, Message: Keys created
    * Code: 400, Message: Missing Required Information
    * Code: 404, Message: Client id not found, DataType: Problem
    */
  override def createKeys(clientId: String, key: Seq[Key])(implicit
    toEntityMarshallerKeysResponse: ToEntityMarshaller[KeysResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Creating keys for client ${clientId}...")

    val validatedPayload: ValidatedNel[String, Seq[Key]] = validateKeys(key)

    validatedPayload match {
      case Valid(validPayload) =>
        val commander: EntityRef[Command] = sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId))
        val result: Future[StatusReply[KeysResponse]] =
          commander.ask(ref => AddKeys(clientId, toKeysMap(validPayload), ref))
        onSuccess(result) {
          case statusReply if statusReply.isSuccess => createKeys201(statusReply.getValue)
          case statusReply if statusReply.isError =>
            createKeys404(Problem(Option(statusReply.getError.getMessage), status = 404, "some error"))
        }

      case Invalid(errors) => createKeys400(Problem(Option(errors.toList.mkString(", ")), status = 400, "some error"))
    }

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
}
