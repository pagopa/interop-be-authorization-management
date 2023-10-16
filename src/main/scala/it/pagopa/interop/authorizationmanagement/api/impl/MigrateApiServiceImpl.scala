package it.pagopa.interop.authorizationmanagement.api.impl

import akka.Done
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import it.pagopa.interop.authorizationmanagement.api.impl.MigrateApiResponseHandlers._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.authorizationmanagement.model.{Problem, UserSeed}
import it.pagopa.interop.authorizationmanagement.api.MigrateApiService

import scala.concurrent.Future

final case class MigrateApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]]
) extends MigrateApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private implicit val settings: ClusterShardingSettings = shardingSettings(entity, system)
  private implicit val implicitSharding: ClusterSharding = sharding

  override def migrateKeyRelationshipToUser(clientId: String, keyId: String, seed: UserSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Migrating key $keyId of client $clientId with user ${seed.userId}"
    logger.info(operationLabel)

    val result: Future[Done] =
      commander(clientId).askWithStatus(ref => MigrateKeyRelationshipToUser(clientId, keyId, seed.userId, ref))

    onComplete(result) {
      migrateKeyRelationshipToUserResponse[Done](operationLabel)(_ => migrateKeyRelationshipToUser204)
    }
  }
}
