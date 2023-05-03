package it.pagopa.interop.authorizationmanagement.server.impl

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import com.atlassian.oai.validator.report.ValidationReport
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.api._
import it.pagopa.interop.authorizationmanagement.api.impl.{
  ClientApiMarshallerImpl,
  ClientApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  KeyApiMarshallerImpl,
  KeyApiServiceImpl,
  PurposeApiMarshallerImpl,
  PurposeApiServiceImpl,
  TokenGenerationApiMarshallerImpl,
  TokenGenerationApiServiceImpl,
  serviceCode
}
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag
}
import it.pagopa.interop.authorizationmanagement.model.persistence.projection.{
  ClientCqrsProjection,
  ClientNotificationProjection,
  KeyCqrsProjection
}
import it.pagopa.interop.authorizationmanagement.model.persistence.{
  AuthorizationEventsSerde,
  Command,
  KeyPersistentBehavior
}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.queue.QueueWriter
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.{Problem => CommonProblem}
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait Dependencies {

  implicit val loggerTI: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog]("OAuth2JWTValidatorAsContexts")

  val keyApiMarshaller: KeyApiMarshaller       = KeyApiMarshallerImpl
  val uuidSupplier: UUIDSupplier               = UUIDSupplier
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplier

  val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
    val index = math.abs(entityContext.entityId.hashCode % numberOfProjectionTags)
    KeyPersistentBehavior(
      entityContext.shard,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      dateTimeSupplier,
      projectionTag(index)
    )
  }

  val keyPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

  val validationExceptionToRoute: ValidationReport => Route = report => {
    val error =
      CommonProblem(StatusCodes.BadRequest, OpenapiUtils.errorFromRequestValidationReport(report), serviceCode, None)
    complete(error.status, error)
  }

  def initProjections(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    initClientCqrsProjection()
    initKeyCqrsProjection()
    initNotificationProjection(blockingEc)
  }

  def initClientCqrsProjection()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val projectionId   = "client-cqrs-projections"
    val cqrsProjection =
      ClientCqrsProjection.projection(dbConfig, ApplicationConfiguration.clientsMongoDB, projectionId)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = projectionId,
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(cqrsProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def initKeyCqrsProjection()(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val projectionId   = "key-cqrs-projections"
    val cqrsProjection = KeyCqrsProjection.projection(dbConfig, ApplicationConfiguration.keysMongoDB, projectionId)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = projectionId,
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(cqrsProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def initNotificationProjection(
    blockingEc: ExecutionContextExecutor
  )(implicit actorSystem: ActorSystem[_], ec: ExecutionContext): Unit = {
    val queueWriter: QueueWriter =
      QueueWriter.get(ApplicationConfiguration.queueUrl)(AuthorizationEventsSerde.authToJson)(blockingEc)

    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val notificationProjectionId: String = "authorization-notification-projections"

    val clientNotificationProjection: ClientNotificationProjection =
      new ClientNotificationProjection(dbConfig, queueWriter, notificationProjectionId)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = notificationProjectionId,
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(clientNotificationProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def keyApi(jwtReader: JWTReader, sharding: ClusterSharding)(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ) = new KeyApi(
    KeyApiServiceImpl(actorSystem, sharding, keyPersistentEntity, dateTimeSupplier),
    keyApiMarshaller,
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  def clientApi(jwtReader: JWTReader, sharding: ClusterSharding)(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ) = new ClientApi(
    ClientApiServiceImpl(actorSystem, sharding, keyPersistentEntity, uuidSupplier),
    ClientApiMarshallerImpl,
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  def purposeApi(jwtReader: JWTReader, sharding: ClusterSharding)(implicit
    ec: ExecutionContext,
    actorSystem: ActorSystem[_]
  ) = new PurposeApi(
    PurposeApiServiceImpl(actorSystem, sharding, keyPersistentEntity, uuidSupplier),
    PurposeApiMarshallerImpl,
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  def tokenGenerationApi(sharding: ClusterSharding)(implicit ec: ExecutionContext, actorSystem: ActorSystem[_]) =
    new TokenGenerationApi(
      TokenGenerationApiServiceImpl(actorSystem, sharding, keyPersistentEntity),
      TokenGenerationApiMarshallerImpl,
      SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
    )

  val healthApi: HealthApi = new HealthApi(
    HealthServiceApiImpl,
    HealthApiMarshallerImpl,
    SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator),
    loggingEnabled = false
  )

  def getJwtValidator(): Future[JWTReader] = JWTConfiguration.jwtReader
    .loadKeyset()
    .map(keyset =>
      new DefaultJWTReader with PublicKeysHolder {
        var publicKeyset: Map[KID, SerializedKey]                                        = keyset
        override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
          getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
      }
    )
    .toFuture

}
