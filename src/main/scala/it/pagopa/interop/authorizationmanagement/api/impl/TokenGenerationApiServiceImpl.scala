package it.pagopa.interop.authorizationmanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.api.TokenGenerationApiService
import it.pagopa.interop.authorizationmanagement.api.impl.TokenGenerationApiResponseHandlers._
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.jwk.converter.KeyConverter
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence.KeyAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._

import scala.concurrent.{ExecutionContext, Future}

final case class TokenGenerationApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
)(implicit ec: ExecutionContext)
    extends TokenGenerationApiService
    with Validation {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private implicit val settings: ClusterShardingSettings = shardingSettings(entity, system)
  private implicit val implicitSharding: ClusterSharding = sharding

  override def getKeyWithClientByKeyId(clientId: String, keyId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerKeyWithClient: ToEntityMarshaller[KeyWithClient],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Getting client $clientId and key $keyId"
    logger.info(operationLabel)

    val result: Future[KeyWithClient] =
      for {
        (persistentClient, persistentKey) <- commander(clientId).askWithStatus(ref =>
          GetKeyWithClient(clientId, keyId, ref)
        )
        jwk                               <- KeyConverter
          .fromBase64encodedPEMToAPIKey(
            persistentKey.kid,
            persistentKey.encodedPem,
            persistentKey.use.toJwk,
            persistentKey.algorithm
          )
          .toFuture
      } yield KeyWithClient(key = jwk.toApi, client = persistentClient.toApi)

    onComplete(result) { getClientKeyByIdResponseResponse[KeyWithClient](operationLabel)(getKeyWithClientByKeyId200) }
  }

}
