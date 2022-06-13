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
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.authorizationmanagement.model.persistence.key.PersistentKey
import it.pagopa.interop.authorizationmanagement.model.persistence.key.PersistentKey.toAPI
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard

import scala.concurrent.{ExecutionContextExecutor, Future}

final case class TokenGenerationApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
) extends TokenGenerationApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private val settings: ClusterShardingSettings = shardingSettings(entity, system)

  implicit val ec: ExecutionContextExecutor = system.executionContext

  override def getClientAndKeyByKeyId(clientId: String, keyId: String)(implicit
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
        val result                            = for {
          apiClient <- persistentClient.toApi
          apiKey    <- toAPI(persistentKey)
        } yield KeyWithClient(
          key = apiKey.key,
          client = apiClient,
          relationshipId = apiKey.relationshipId,
          name = apiKey.name,
          createdAt = apiKey.createdAt
        )

        result match {
          case Right(value) => getClientAndKeyByKeyId200(value)
          case Left(err)    => complete(500, err)
        }

      case statusReply =>
        logger.info(
          "Error while getting client and key for key {} and client {}",
          keyId,
          clientId,
          statusReply.getError
        )
        getClientAndKeyByKeyId404(problemOf(StatusCodes.NotFound, ClientKeyNotFound(clientId, keyId)))
    }
  }
}
