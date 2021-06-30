package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.Key.toAPI

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object KeyPersistentBehavior {

  final case object KeyNotFoundException extends Throwable

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout = context.system.settings.config.getDuration("pdnd-interop-uservice-key-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddKeys(clientId, keys, replyTo) =>
        Effect
          .persist(KeysAdded(clientId, keys))
          .thenRun(_ => replyTo ! StatusReply.Success(toAPI(clientId, keys)))

      case GetKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(key) =>
            replyTo ! StatusReply.Success(key)
            Effect.none[Event, State]
          case None => commandError(replyTo)
        }

      case GetKeys(clientId, replyTo) =>
        state.getClientKeys(clientId) match {
          case Some(keys) =>
            replyTo ! StatusReply.Success(toAPI(clientId, keys))
            Effect.none[Event, State]
          case None => commandError(replyTo)
        }

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.error(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  private def commandError[T](replyTo: ActorRef[StatusReply[T]]): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](KeyNotFoundException)
    Effect.none[Event, State]
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case KeysAdded(clientId, keys) => state.addKeys(clientId, keys)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("pdnd-interop-uservice-pdnd_uservice_key_management_persistence")

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.error(s"Starting Pet Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config.getInt("pdnd-interop-uservice-key-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
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
