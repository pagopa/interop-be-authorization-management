package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.State

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

@SuppressWarnings(Array("org.wartremover.warts.Equals", "org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
object ClientPersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[ClientCommand]
  ): (State, ClientCommand) => Effect[ClientEvent, State] = { (state, command) =>
    val idleTimeout = context.system.settings.config.getDuration("pdnd-interop-uservice-key-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddClient(persistentClient, replyTo) =>
        val client: Option[PersistentClient] = state.clients.get(persistentClient.id.toString)

        client
          .map { c =>
            replyTo ! StatusReply.Error[PersistentClient](s"Client ${c.id.toString} already exists")
            Effect.none[ClientAdded, State]
          }
          .getOrElse {
            Effect
              .persist(ClientAdded(persistentClient))
              .thenRun((_: State) => replyTo ! StatusReply.Success(persistentClient))
          }

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.error(s"Passivate shard: ${shard.path.name}")
        Effect.none[ClientEvent, State]
    }
  }

  val eventHandler: (State, ClientEvent) => State = (state, event) =>
    event match {
      case ClientAdded(client) => state.addClient(client)
    }

  val TypeKey: EntityTypeKey[ClientCommand] =
    EntityTypeKey[ClientCommand]("pdnd-interop-uservice-pdnd-uservice-key-management-client-persistence")

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], persistenceId: PersistenceId): Behavior[ClientCommand] = {
    Behaviors.setup { context =>
      context.log.error(s"Starting Key Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config.getInt("pdnd-interop-uservice-key-management.number-of-events-before-snapshot")
      EventSourcedBehavior[ClientCommand, ClientEvent, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(persistenceId.id))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }
}
