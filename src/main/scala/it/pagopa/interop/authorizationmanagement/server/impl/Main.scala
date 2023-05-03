package it.pagopa.interop.authorizationmanagement.server.impl

import cats.syntax.all._
import buildinfo.BuildInfo
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.cluster.ClusterEvent
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.authorizationmanagement.server.Controller
import it.pagopa.interop.commons.logging.renderBuildInfo
import scala.concurrent.ExecutionContext
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.util.{Success, Failure}
import akka.actor.typed.DispatcherSelector
import scala.concurrent.ExecutionContextExecutor

object Main extends App with Dependencies {

  val logger: Logger = Logger(this.getClass)

  ActorSystem[Nothing](
    Behaviors.setup[Nothing] { context =>
      implicit val actorSystem: ActorSystem[_]        = context.system
      implicit val executionContext: ExecutionContext = actorSystem.executionContext
      // Let's keep it here in the case we'll need to call any external service
      val selector: DispatcherSelector                = DispatcherSelector.fromConfig("futures-dispatcher")
      val blockingEc: ExecutionContextExecutor        = actorSystem.dispatchers.lookup(selector)

      AkkaManagement.get(actorSystem).start()

      val sharding: ClusterSharding = ClusterSharding(context.system)
      sharding.init(keyPersistentEntity)

      val cluster: Cluster = Cluster(context.system)
      ClusterBootstrap.get(actorSystem).start()

      val listener = context.spawn(
        Behaviors.receive[ClusterEvent.MemberEvent]((ctx, event) => {
          ctx.log.info("MemberEvent: {}", event)
          Behaviors.same
        }),
        "listener"
      )

      cluster.subscriptions ! Subscribe(listener, classOf[ClusterEvent.MemberEvent])

      if (ApplicationConfiguration.projectionsEnabled) initProjections(blockingEc)

      logger.info(renderBuildInfo(BuildInfo))
      logger.info(s"Started cluster at ${cluster.selfMember.address}")

      val serverBinding: Future[ServerBinding] = for {
        jwtValidator <- getJwtValidator()
        controller = new Controller(
          clientApi(jwtValidator, sharding),
          healthApi,
          keyApi(jwtValidator, sharding),
          purposeApi(jwtValidator, sharding),
          tokenGenerationApi(sharding),
          validationExceptionToRoute.some
        )(actorSystem.classicSystem)
        binding <- Http().newServerAt("0.0.0.0", ApplicationConfiguration.serverPort).bind(controller.routes)
      } yield binding

      serverBinding.onComplete {
        case Success(b) =>
          logger.info(s"Started server at ${b.localAddress.getHostString}:${b.localAddress.getPort}")
        case Failure(e) =>
          actorSystem.terminate()
          logger.error("Startup error: ", e)
      }

      Behaviors.empty
    },
    BuildInfo.name
  )
}
