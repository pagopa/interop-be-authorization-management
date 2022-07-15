package it.pagopa.interop.authorizationmanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onSuccess}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.authorizationmanagement.api.TokenGenerationApiService
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence.KeyAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard

import scala.concurrent.Future

final case class TokenGenerationApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
) extends TokenGenerationApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val settings: ClusterShardingSettings = shardingSettings(entity, system)

  override def getKeyWithClientByKeyId(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerKeyWithClient: ToEntityMarshaller[KeyWithClient],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Getting client and key for key {} and client {}", keyId, clientId)
    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[(PersistentClient, PersistentKey)]] =
      commander.ask(ref => GetKeyWithClient(clientId, keyId, ref))

    onSuccess(result) {
      case statusReply if statusReply.isSuccess =>
        val (persistentClient, persistentKey) = statusReply.getValue

        val result: Either[Throwable, KeyWithClient] =
          persistentKey.toApi.map(apiKey => KeyWithClient(key = apiKey.key, client = persistentClient.toApi))

        result.fold(err => internalServerError("Key with Client retrieve", err.getMessage), getKeyWithClientByKeyId200)

      case statusReply =>
        logger.info(
          "Error while getting client and key for key {} and client {}",
          keyId,
          clientId,
          statusReply.getError
        )
        getKeyWithClientByKeyId404(problemOf(StatusCodes.NotFound, ClientKeyNotFound(clientId, keyId)))
    }
  }

  def internalServerError(operation: String, reason: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.error(s"Error on $operation with reason $reason")
    complete(StatusCodes.InternalServerError, problemOf(StatusCodes.InternalServerError, GenericError(reason)))
  }

}
