package it.pagopa.interop.authorizationmanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.onSuccess
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.authorizationmanagement.api.KeyApiService
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

final case class KeyApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
) extends KeyApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  private val settings: ClusterShardingSettings = shardingSettings(entity, system)

  /** Code: 201, Message: Keys created
    * Code: 400, Message: Missing Required Information
    * Code: 404, Message: Client id not found, DataType: Problem
    */
  override def createKeys(clientId: String, key: Seq[KeySeed])(implicit
    toEntityMarshallerKeysResponse: ToEntityMarshaller[KeysResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    // TODO consider a preauthorize for user rights validations...
    logger.info("Creating keys for client {}", clientId)
    val validatedPayload: ValidatedNel[String, Seq[ValidKey]] = validateKeys(key)

    validatedPayload.andThen(k => validateWithCurrentKeys(k, keysIdentifiers)) match {
      case Valid(validKeys) =>
        val commander: EntityRef[Command]             =
          sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))
        val result: Future[StatusReply[KeysResponse]] =
          commander.ask(ref => AddKeys(clientId, validKeys, ref))
        onSuccess(result) {
          case statusReply if statusReply.isSuccess => createKeys201(statusReply.getValue)
          case statusReply                          =>
            logger.error(s"Error while creating keys for client $clientId - ${statusReply.getError.getMessage}")
            createKeys400(problemOf(StatusCodes.BadRequest, CreateKeysBadRequest(clientId: String)))
        }

      case Invalid(errors) =>
        val errorsStr = errors.toList.mkString(", ")
        logger.error(s"Error while creating keys for client ${clientId} - ${errorsStr}")
        createKeys400(problemOf(StatusCodes.BadRequest, CreateKeysInvalid(clientId)))
    }
  }

  /** Code: 200, Message: List of keyIdentifiers, DataType: Seq[Pet]
    */
  private def keysIdentifiers: LazyList[Kid] = {
    val sliceSize                           = 1000
    val commanders: Seq[EntityRef[Command]] = (0 until settings.numberOfShards).map(shard =>
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
    )
    val keyIdentifiers: LazyList[Kid]       = commanders.to(LazyList).flatMap(ref => slices(ref, sliceSize))

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
    toEntityMarshallerClientKey: ToEntityMarshaller[ClientKey],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Getting key {} for client {}", keyId, clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[ClientKey]] = commander.ask(ref => GetKey(clientId, keyId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getClientKeyById200(statusReply.getValue)
      case statusReply                          =>
        logger.info("Error while getting key {} for client {}", keyId, clientId, statusReply.getError)
        getClientKeyById404(problemOf(StatusCodes.NotFound, ClientKeyNotFound(clientId, keyId)))
    }
  }

  /** Code: 200, Message: returns the corresponding array of keys, DataType: KeysCreatedResponse
    * Code: 404, Message: Client id not found, DataType: Problem
    */
  override def getClientKeys(clientId: String)(implicit
    toEntityMarshallerKeysCreatedResponse: ToEntityMarshaller[KeysResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Getting keys for client {}", clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[KeysResponse]] = commander.ask(ref => GetKeys(clientId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getClientKeys200(statusReply.getValue)
      case statusReply                          =>
        logger.info("Error while getting keys for client {}", clientId, statusReply.getError)
        getClientKeys404(problemOf(StatusCodes.NotFound, ClientKeysNotFound(clientId)))
    }
  }

  /** Code: 204, Message: the corresponding key has been deleted.
    * Code: 404, Message: Key not found, DataType: Problem
    */
  override def deleteClientKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Deleting key {} belonging to {}", keyId, clientId)
    val commander: EntityRef[Command]     =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))
    val result: Future[StatusReply[Done]] = commander.ask(ref => DeleteKey(clientId, keyId, ref))
    onSuccess(result) {
      case statusReply if statusReply.isSuccess => deleteClientKeyById204
      case statusReply                          =>
        logger.info("Error while deleting key {} belonging to {}", keyId, clientId, statusReply.getError)
        deleteClientKeyById404(problemOf(StatusCodes.BadRequest, DeleteClientKeyNotFound(clientId, keyId)))
    }
  }

  /** Code: 200, Message: returns the corresponding base 64 encoded key, DataType: EncodedClientKey
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Key not found, DataType: Problem
    * Code: 500, Message: Internal Server Error, DataType: Problem
    */
  override def getEncodedClientKeyById(clientId: String, keyId: String)(implicit
    toEntityMarshallerEncodedClientKey: ToEntityMarshaller[EncodedClientKey],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Getting encoded key {} for client {}", keyId, clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[EncodedClientKey]] = commander.ask(ref => GetEncodedKey(clientId, keyId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess => getEncodedClientKeyById200(statusReply.getValue)
      case statusReply                          =>
        logger.info("Error while getting encoded key {} for client {}", keyId, clientId, statusReply.getError)
        getEncodedClientKeyById404(problemOf(StatusCodes.NotFound, EncodedClientKeyNotFound(clientId, keyId)))
    }
  }
}
