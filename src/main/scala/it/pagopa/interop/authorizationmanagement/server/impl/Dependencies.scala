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
  problemOf
}
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration.{
  numberOfProjectionTags,
  projectionTag
}
import it.pagopa.interop.authorizationmanagement.model.persistence.{
  Command,
  KeyPersistentBehavior,
  KeyPersistentProjection
}
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.service.{OffsetDateTimeSupplier, UUIDSupplier}
import it.pagopa.interop.commons.utils.service.impl.{OffsetDateTimeSupplierImpl, UUIDSupplierImpl}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait Dependencies {

  val keyApiMarshaller: KeyApiMarshaller       = KeyApiMarshallerImpl
  val uuidSupplier: UUIDSupplier               = new UUIDSupplierImpl()
  val dateTimeSupplier: OffsetDateTimeSupplier = OffsetDateTimeSupplierImpl

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
      problemOf(StatusCodes.BadRequest, ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report)))
    complete(error.status, error)(keyApiMarshaller.toEntityMarshallerProblem)
  }

  def initProjections()(implicit actorSystem: ActorSystem[_]): Unit = {
    val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

    val keyPersistentProjection = new KeyPersistentProjection(actorSystem, dbConfig)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = "keys-projections",
      numberOfInstances = numberOfProjectionTags,
      behaviorFactory = (i: Int) => ProjectionBehavior(keyPersistentProjection.projection(projectionTag(i))),
      stopMessage = ProjectionBehavior.Stop
    )
  }

  def keyApi(jwtReader: JWTReader, sharding: ClusterSharding)(implicit actorSystem: ActorSystem[_]) = new KeyApi(
    KeyApiServiceImpl(actorSystem, sharding, keyPersistentEntity),
    keyApiMarshaller,
    jwtReader.OAuth2JWTValidatorAsContexts
  )

  def clientApi(jwtReader: JWTReader, sharding: ClusterSharding)(implicit actorSystem: ActorSystem[_]) = new ClientApi(
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

  def tokenGenerationApi(sharding: ClusterSharding)(implicit actorSystem: ActorSystem[_]) =
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
