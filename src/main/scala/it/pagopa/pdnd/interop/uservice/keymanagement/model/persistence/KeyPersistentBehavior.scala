package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.KeysResponse
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{Active, Deleted}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey.{toAPI, toAPIResponse}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object KeyPersistentBehavior {

  final case object KeyNotFoundException extends Throwable

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout = context.system.settings.config.getDuration("pdnd-interop-uservice-key-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddKeys(clientId, keys, replyTo) =>
        state
          .containsKeys(clientId, keys.map(_._1).toSeq) match {
          case Some(existingKeys) =>
            replyTo ! StatusReply.Error[KeysResponse](
              s"The keys identified by: '${existingKeys.mkString(", ")}' already exist for this client"
            )
            Effect.none[Event, State]
          case None =>
            Effect
              .persist(KeysAdded(clientId, keys))
              .thenRun(_ => replyTo ! StatusReply.Success(toAPIResponse(clientId, keys)))
        }

      case GetKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(key) =>
            replyTo ! StatusReply.Success(toAPI(key))
            Effect.none[Event, State]
          case None => commandError(replyTo)
        }

      case DisableKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(key) if !key.status.equals(Active) =>
            replyTo ! StatusReply.Error[Done](s"Key ${keyId} of client ${clientId} is already disabled")
            Effect.none[KeyDisabled, State]
          case Some(_) => {
            Effect
              .persist(KeyDisabled(clientId, keyId, OffsetDateTime.now()))
              .thenRun(_ => replyTo ! StatusReply.Success(Done))
          }
          case None => commandError(replyTo)
        }

      case DeleteKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(key) if key.status.equals(Deleted) =>
            replyTo ! StatusReply.Error[Done](s"Key ${keyId} of client ${clientId} is already deleted")
            Effect.none[KeyDeleted, State]
          case Some(_) =>
            Effect
              .persist(KeyDeleted(clientId, keyId, OffsetDateTime.now()))
              .thenRun(_ => replyTo ! StatusReply.Success(Done))
          case None => commandError(replyTo)
        }

      case GetKeys(clientId, replyTo) =>
        state.getClientKeys(clientId) match {
          case Some(keys) =>
            replyTo ! StatusReply.Success(toAPIResponse(clientId, keys))
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
      case KeysAdded(clientId, keys)               => state.addKeys(clientId, keys)
      case KeyDisabled(clientId, keyId, timestamp) => state.disable(clientId, keyId, timestamp)
      case KeyDeleted(clientId, keyId, timestamp)  => state.delete(clientId, keyId, timestamp)
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
