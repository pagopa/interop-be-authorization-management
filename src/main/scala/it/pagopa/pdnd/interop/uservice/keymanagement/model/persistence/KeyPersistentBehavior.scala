package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.keymanagement.model.KeysResponse
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey.{
  toAPI,
  toAPIResponse,
  toPersistentKey
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{Active, Disabled, PersistentKey}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

@SuppressWarnings(Array("org.wartremover.warts.Equals", "org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
object KeyPersistentBehavior {

  final case object KeyNotFoundException extends Throwable

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout = context.system.settings.config.getDuration("pdnd-interop-uservice-key-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddKeys(clientId, validKeys, replyTo) =>
        validKeys
          .map(toPersistentKey)
          .sequence
          .fold(
            t => {
              replyTo ! StatusReply
                .Error[KeysResponse](s"Error while calculating keys thumbprints: ${t.getLocalizedMessage}")
              Effect.none[Event, State]
            },
            keys => addKeys(replyTo, clientId, keys)
          )

      case GetKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(key) =>
            toAPI(key).fold(
              error => errorMessageReply(replyTo, s"Error while retrieving key: ${error.getLocalizedMessage}"),
              key => {
                replyTo ! StatusReply.Success(key)
                Effect.none[Event, State]
              }
            )

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

      case EnableKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(key) if !key.status.equals(Disabled) =>
            replyTo ! StatusReply.Error[Done](s"Key ${keyId} of client ${clientId} is not disabled")
            Effect.none[KeyEnabled, State]
          case Some(_) => {
            Effect
              .persist(KeyEnabled(clientId, keyId))
              .thenRun(_ => replyTo ! StatusReply.Success(Done))
          }
          case None => commandError(replyTo)
        }

      case DeleteKey(clientId, keyId, replyTo) =>
        state.getClientKeyByKeyId(clientId, keyId) match {
          case Some(_) =>
            Effect
              .persist(KeyDeleted(clientId, keyId, OffsetDateTime.now()))
              .thenRun(_ => replyTo ! StatusReply.Success(Done))
          case None => commandError(replyTo)
        }

      case GetKeys(clientId, replyTo) =>
        state.getClientKeys(clientId) match {
          case Some(keys) =>
            toAPIResponse(keys).fold(
              error => errorMessageReply(replyTo, s"Error while retrieving keys: ${error.getLocalizedMessage}"),
              keys => {
                replyTo ! StatusReply.Success(keys)
                Effect.none[Event, State]
              }
            )

          case None => commandError(replyTo)
        }

      case ListKid(from: Int, until: Int, replyTo) =>
        replyTo ! StatusReply.Success(state.keys.values.toSeq.slice(from, until).flatMap(_.keys))
        Effect.none[Event, State]

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.error(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  private def errorMessageReply[T](replyTo: ActorRef[StatusReply[T]], message: String): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](message)
    Effect.none[KeyDeleted, State]
  }

  private def addKeys(
    replyTo: ActorRef[StatusReply[KeysResponse]],
    clientId: String,
    keys: Seq[PersistentKey]
  ): Effect[Event, State] = {
    val mapKeys = keys.map(k => k.kid -> k).toMap

    toAPIResponse(mapKeys).fold[Effect[Event, State]](
      t => {
        replyTo ! StatusReply.Error[KeysResponse](s"Error while building response object ${t.getLocalizedMessage}")
        Effect.none[KeyDeleted, State]
      },
      response =>
        Effect
          .persist(KeysAdded(clientId, mapKeys))
          .thenRun(_ => replyTo ! StatusReply.Success(response))
    )

  }

  private def commandError[T](replyTo: ActorRef[StatusReply[T]]): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](KeyNotFoundException)
    Effect.none[Event, State]
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case KeysAdded(clientId, keys)               => state.addKeys(clientId, keys)
      case KeyDisabled(clientId, keyId, timestamp) => state.disable(clientId, keyId, timestamp)
      case KeyEnabled(clientId, keyId)             => state.enable(clientId, keyId)
      case KeyDeleted(clientId, keyId, _)          => state.delete(clientId, keyId)
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
