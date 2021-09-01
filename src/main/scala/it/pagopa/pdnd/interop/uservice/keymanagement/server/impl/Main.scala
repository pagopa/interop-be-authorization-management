package it.pagopa.pdnd.interop.uservice.keymanagement.server.impl

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, ShardedDaemonProcess}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.directives.SecurityDirectives
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.persistence.typed.PersistenceId
import akka.projection.ProjectionBehavior
import akka.{actor => classic}
import it.pagopa.pdnd.interop.uservice.keymanagement.api.{ClientApi, HealthApi, KeyApi}
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl.{
  ClientApiMarshallerImpl,
  ClientApiServiceImpl,
  HealthApiMarshallerImpl,
  HealthServiceApiImpl,
  KeyApiMarshallerImpl,
  KeyApiServiceImpl
}
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system.Authenticator
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Problem
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.{ClientCommand, ClientPersistentBehavior}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{
  Command,
  KeyPersistentBehavior,
  KeyPersistentProjection
}
import it.pagopa.pdnd.interop.uservice.keymanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.UUIDSupplierImpl
import kamon.Kamon

import scala.jdk.CollectionConverters._

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny", "org.wartremover.warts.Nothing"))
object Main extends App {

  Kamon.init()

  locally {
    val _ = ActorSystem[Nothing](
      Behaviors.setup[Nothing] { context =>
        import akka.actor.typed.scaladsl.adapter._
        implicit val classicSystem: classic.ActorSystem = context.system.toClassic
        val marshallerImpl                              = new KeyApiMarshallerImpl()
        val uuidSupplierImpl                            = new UUIDSupplierImpl()

        val cluster = Cluster(context.system)

        context.log.error("Started [" + context.system + "], cluster.selfAddress = " + cluster.selfMember.address + ")")

        val sharding: ClusterSharding = ClusterSharding(context.system)

        val keyPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
          Entity(typeKey = KeyPersistentBehavior.TypeKey) { entityContext =>
            KeyPersistentBehavior(
              entityContext.shard,
              PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
            )
          }

        val clientPersistentEntity: Entity[ClientCommand, ShardingEnvelope[ClientCommand]] =
          Entity(typeKey = ClientPersistentBehavior.TypeKey) { entityContext =>
            ClientPersistentBehavior(
              entityContext.shard,
              PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
            )
          }

        val _ = sharding.init(keyPersistentEntity)

        val settings: ClusterShardingSettings = keyPersistentEntity.settings match {
          case None    => ClusterShardingSettings(context.system)
          case Some(s) => s
        }

        val persistence =
          classicSystem.classicSystem.settings.config.getString("pdnd-interop-uservice-key-management.persistence")
        if (persistence == "cassandra") {
          val keyPersistentProjection = new KeyPersistentProjection(context.system, keyPersistentEntity)

          ShardedDaemonProcess(context.system).init[ProjectionBehavior.Command](
            name = "keys-projections",
            numberOfInstances = settings.numberOfShards,
            behaviorFactory = (i: Int) => ProjectionBehavior(keyPersistentProjection.projections(i)),
            stopMessage = ProjectionBehavior.Stop
          )
        }

        val keyApi = new KeyApi(
          new KeyApiServiceImpl(context.system, sharding, keyPersistentEntity),
          marshallerImpl,
          SecurityDirectives.authenticateBasic("SecurityRealm", Authenticator)
        )

        val clientApi = new ClientApi(
          new ClientApiServiceImpl(context.system, sharding, clientPersistentEntity, uuidSupplierImpl),
          new ClientApiMarshallerImpl(),
          SecurityDirectives.authenticateBasic("SecurityRealm", Authenticator)
        )

        val healthApi: HealthApi = new HealthApi(
          new HealthServiceApiImpl(),
          new HealthApiMarshallerImpl(),
          SecurityDirectives.authenticateBasic("SecurityRealm", Authenticator)
        )

        val _ = AkkaManagement.get(classicSystem).start()

        val controller = new Controller(
          clientApi,
          healthApi,
          keyApi,
          validationExceptionToRoute = Some(e => {
            val results = e.results()
            results.crumbs().asScala.foreach { crumb =>
              println(crumb.crumb())
            }
            results.items().asScala.foreach { item =>
              println(item.dataCrumbs())
              println(item.dataJsonPointer())
              println(item.schemaCrumbs())
              println(item.message())
              println(item.severity())
            }
            val message = e.results().items().asScala.map(_.message()).mkString("\n")
            complete(400, Problem(Some(message), 400, "bad request"))(marshallerImpl.toEntityMarshallerProblem)
          })
        )

        val _ = Http().newServerAt("0.0.0.0", 8088).bind(controller.routes)

        val listener = context.spawn(
          Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
            ctx.log.error("MemberEvent: {}", event)
            Behaviors.same
          }),
          "listener"
        )

        Cluster(context.system).subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

        val _ = AkkaManagement(classicSystem).start()
        ClusterBootstrap.get(classicSystem).start()
        Behaviors.empty
      },
      "pdnd-interop-uservice-key-management"
    )
  }
}
