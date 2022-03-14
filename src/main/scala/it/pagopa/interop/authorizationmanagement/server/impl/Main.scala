package it.pagopa.interop.authorizationmanagement.server.impl

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, ShardedDaemonProcess}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
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
import it.pagopa.interop.authorizationmanagement.server.Controller
import it.pagopa.interop.commons.jwt.service.JWTReader
import it.pagopa.interop.commons.jwt.service.impl.{DefaultJWTReader, getClaimsVerifier}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, KID, PublicKeysHolder, SerializedKey}
import it.pagopa.interop.commons.utils.AkkaUtils.PassThroughAuthenticator
import it.pagopa.interop.commons.utils.OpenapiUtils
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.ValidationRequestError
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import it.pagopa.interop.commons.utils.service.impl.UUIDSupplierImpl
import kamon.Kamon
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext
import scala.util.Try

object Main extends App {

  val dependenciesLoaded: Try[JWTReader] = for {
    keyset <- JWTConfiguration.jwtReader.loadKeyset()
    jwtValidator = new DefaultJWTReader with PublicKeysHolder {
      var publicKeyset: Map[KID, SerializedKey] = keyset
      override protected val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
        getClaimsVerifier(audience = ApplicationConfiguration.jwtAudience)
    }
  } yield jwtValidator

  val jwtValidator =
    dependenciesLoaded.get //THIS IS THE END OF THE WORLD. Exceptions are welcomed here.

  Kamon.init()

  lazy val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
    val index = math.abs(entityContext.entityId.hashCode % numberOfProjectionTags)
    KeyPersistentBehavior(
      entityContext.shard,
      PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
      projectionTag(index)
    )
  }

  locally {
    val _ = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        import akka.actor.typed.scaladsl.adapter._
        implicit val classicSystem: classic.ActorSystem = context.system.toClassic
        implicit val executionContext: ExecutionContext = context.system.executionContext

        val keyApiMarshaller: KeyApiMarshaller = KeyApiMarshallerImpl
        val uuidSupplier: UUIDSupplier         = new UUIDSupplierImpl()

        val cluster = Cluster(context.system)

        context.log.info(
          "Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ", build information = " + buildinfo.BuildInfo.toString + ")"
        )

        val sharding: ClusterSharding = ClusterSharding(context.system)

        val keyPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
          Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

        val _ = sharding.init(keyPersistentEntity)

        val persistence = classicSystem.classicSystem.settings.config.getString("akka.persistence.journal.plugin")
        val enabled     = false
        if (persistence == "jdbc-journal" && enabled) {
          val dbConfig: DatabaseConfig[JdbcProfile] =
            DatabaseConfig.forConfig("akka-persistence-jdbc.shared-databases.slick")

          val keyPersistentProjection = new KeyPersistentProjection(context.system, dbConfig)

          ShardedDaemonProcess(context.system).init[ProjectionBehavior.Command](
            name = "keys-projections",
            numberOfInstances = numberOfProjectionTags,
            behaviorFactory = (i: Int) => ProjectionBehavior(keyPersistentProjection.projection(projectionTag(i))),
            stopMessage = ProjectionBehavior.Stop
          )
        }

        val keyApi = new KeyApi(
          KeyApiServiceImpl(context.system, sharding, keyPersistentEntity),
          keyApiMarshaller,
          SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
        )

        val clientApi = new ClientApi(
          ClientApiServiceImpl(context.system, sharding, keyPersistentEntity, uuidSupplier),
          ClientApiMarshallerImpl,
          SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
        )

        val purposeApi = new PurposeApi(
          PurposeApiServiceImpl(context.system, sharding, keyPersistentEntity, uuidSupplier),
          PurposeApiMarshallerImpl,
          jwtValidator.OAuth2JWTValidatorAsContexts
        )

        val healthApi: HealthApi = new HealthApi(
          HealthServiceApiImpl,
          HealthApiMarshallerImpl,
          SecurityDirectives.authenticateOAuth2("SecurityRealm", PassThroughAuthenticator)
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          clientApi,
          healthApi,
          keyApi,
          purposeApi,
          validationExceptionToRoute = Some(report => {
            val error =
              problemOf(
                StatusCodes.BadRequest,
                ValidationRequestError(OpenapiUtils.errorFromRequestValidationReport(report))
              )
            complete(error.status, error)(keyApiMarshaller.toEntityMarshallerProblem)
          })
        )

        val _ = Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)

        val listener = context.spawn(
          Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
            ctx.log.info("MemberEvent: {}", event)
            Behaviors.same
          }),
          "listener"
        )

        Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

        val _ = AkkaManagement(classicSystem).start()
        ClusterBootstrap.get(classicSystem).start()
        Behaviors.empty
      },
      "interop-be-authorization-management"
    )
  }
}
