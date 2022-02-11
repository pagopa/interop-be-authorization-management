package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.KeyManagementErrors._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey.{
  toAPI,
  toAPIResponse,
  toPersistentKey
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{EncodedClientKey, KeysResponse}

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object KeyPersistentBehavior {

  final case object KeyNotFoundException extends Throwable

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command]
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout = context.system.settings.config.getDuration("key-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddKeys(clientId, validKeys, replyTo) =>
        state.clients.get(clientId) match {
          case Some(client) =>
            val persistentKeys: Either[Throwable, Seq[PersistentKey]] = for {
              _ <- validateRelationships(client, validKeys)
              persistentKeys <- validKeys
                .map(toPersistentKey)
                .sequence
            } yield persistentKeys

            persistentKeys
              .fold(error => commandError(replyTo, error), keys => addKeys(replyTo, clientId, keys))

          case None => commandError(replyTo, ClientNotFoundError(clientId))
        }

      case GetKey(clientId, keyId, replyTo) =>
        state.getClientKeyById(clientId, keyId) match {
          case Some(key) =>
            toAPI(key).fold(
              error => errorMessageReply(replyTo, s"Error while retrieving key: ${error.getLocalizedMessage}"),
              key => {
                replyTo ! StatusReply.Success(key)
                Effect.none[Event, State]
              }
            )

          case None => commandKeyNotFoundError(replyTo)
        }

      case GetEncodedKey(clientId, keyId, replyTo) =>
        state.getClientKeyById(clientId, keyId) match {
          case Some(key) =>
            replyTo ! StatusReply.Success(EncodedClientKey(key = key.encodedPem))
            Effect.none[Event, State]
          case None => commandKeyNotFoundError(replyTo)
        }

      case DeleteKey(clientId, keyId, replyTo) =>
        state.getClientKeyById(clientId, keyId) match {
          case Some(_) =>
            Effect
              .persist(KeyDeleted(clientId, keyId, OffsetDateTime.now()))
              .thenRun(_ => replyTo ! StatusReply.Success(Done))
          case None => commandKeyNotFoundError(replyTo)
        }

      case GetKeys(clientId, replyTo) =>
        state.keys.get(clientId) match {
          case Some(keys) =>
            toAPIResponse(keys).fold(
              error => errorMessageReply(replyTo, s"Error while retrieving keys: ${error.getLocalizedMessage}"),
              keys => {
                replyTo ! StatusReply.Success(keys)
                Effect.none[Event, State]
              }
            )

          case None => commandKeyNotFoundError(replyTo)
        }

      case ListKid(from: Int, until: Int, replyTo) =>
        replyTo ! StatusReply.Success(state.keys.values.toSeq.slice(from, until).flatMap(_.keys))
        Effect.none[Event, State]

      // TODO Client commands should be in a separated behavior
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

      case GetClient(clientId, replyTo) =>
        state.clients.get(clientId) match {
          case Some(client) =>
            replyTo ! StatusReply.Success(client)
            Effect.none[Event, State]
          case None => commandError(replyTo, ClientNotFoundError(clientId))
        }

      case ListClients(from, to, relationshipId, consumerId, replyTo) =>
        val filteredClients: Seq[PersistentClient] = state.clients.values.toSeq.filter { client =>
          relationshipId.forall(relationship => client.relationships.map(_.toString).contains(relationship)) &&
          consumerId.forall(_ == client.consumerId.toString)
        }
        val paginatedClients: Seq[PersistentClient] = filteredClients.slice(from, to)

        replyTo ! StatusReply.Success(paginatedClients)
        Effect.none[Event, State]

      case DeleteClient(clientId, replyTo) =>
        val client: Option[PersistentClient] = state.clients.get(clientId)

        client
          .fold(commandError(replyTo, ClientNotFoundError(clientId)))(_ =>
            Effect
              .persist(ClientDeleted(clientId))
              .thenRun((_: State) => replyTo ! StatusReply.Success(Done))
          )

      case AddRelationship(clientId, relationshipId, replyTo) =>
        val client: Option[PersistentClient] = state.clients.get(clientId)

        client
          .fold {
            commandError(replyTo, ClientNotFoundError(clientId))
          } { c =>
            Effect
              .persist(RelationshipAdded(c, relationshipId))
              .thenRun((s: State) =>
                replyTo ! s.clients
                  .get(clientId)
                  .fold[StatusReply[PersistentClient]](
                    StatusReply.Error(new RuntimeException(s"Client $clientId not found after add relationship action"))
                  )(updatedClient => StatusReply.Success(updatedClient))
              )
          }

      case RemoveRelationship(clientId, relationshipId, replyTo) =>
        val client: Option[PersistentClient] = state.clients.get(clientId)

        val validations: Either[Throwable, PersistentClient] = for {
          persistentClient <- client.toRight(ClientNotFoundError(clientId))
          _ <- persistentClient.relationships
            .find(_.toString == relationshipId)
            .toRight(PartyRelationshipNotFoundError(clientId, relationshipId))
        } yield persistentClient

        validations
          .fold(
            error => commandError(replyTo, error),
            { _ =>
              Effect
                .persist(RelationshipRemoved(clientId, relationshipId))
                .thenRun((_: State) => replyTo ! StatusReply.Success(Done))

            }
          )

      case AddClientPurpose(clientId, purpose, replyTo) =>
        val v: Either[ComponentError, Unit] = for {
          client <- state.clients.get(clientId).toRight(ClientNotFoundError(clientId))
          _ <- client.purposes
            .get(purpose.id)
            .toLeft(())
            .left
            .map(_ => PurposeAlreadyExists(clientId, purpose.id.toString))
        } yield ()

        v.fold(
          commandError(replyTo, _),
          _ =>
            Effect
              .persist(ClientPurposeAdded(clientId, purpose.id, purpose.statesChain))
              .thenRun((_: State) => replyTo ! StatusReply.Success(purpose))
        )

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.info(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  private def validateRelationships(
    client: PersistentClient,
    keys: Seq[ValidKey]
  ): Either[PartyRelationshipNotAllowedError, Unit] = {
    val relationshipsNotInClient = keys.map(_._1.relationshipId).toSet -- client.relationships

    Either.cond(
      relationshipsNotInClient.isEmpty,
      (),
      PartyRelationshipNotAllowedError(
        relationshipsNotInClient.map(relationshipId => (relationshipId.toString, client.id.toString))
      )
    )
  }

  private def errorMessageReply[T](replyTo: ActorRef[StatusReply[T]], message: String): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](message)
    Effect.none[Event, State]
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
        Effect.none[KeysAdded, State]
      },
      response =>
        Effect
          .persist(KeysAdded(clientId, mapKeys))
          .thenRun(_ => replyTo ! StatusReply.Success(response))
    )

  }

  private def commandKeyNotFoundError[T](replyTo: ActorRef[StatusReply[T]]): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](KeyNotFoundException)
    Effect.none[Event, State]
  }

  private def commandError[T](replyTo: ActorRef[StatusReply[T]], error: Throwable): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](error)
    Effect.none[Event, State]
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case KeysAdded(clientId, keys)                     => state.addKeys(clientId, keys)
      case KeyDeleted(clientId, keyId, _)                => state.deleteKey(clientId, keyId)
      case ClientAdded(client)                           => state.addClient(client)
      case ClientDeleted(clientId)                       => state.deleteClient(clientId)
      case RelationshipAdded(client, relationshipId)     => state.addRelationship(client, relationshipId)
      case RelationshipRemoved(clientId, relationshipId) => state.removeRelationship(clientId, relationshipId)
      case ClientPurposeAdded(clientId, purposeId, statesChain) =>
        state.addClientPurpose(clientId, purposeId, statesChain)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("pdnd-interop-uservice-pdnd-uservice-key-management-persistence")

  def apply(shard: ActorRef[ClusterSharding.ShardCommand], persistenceId: PersistenceId): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info(s"Starting Key Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config.getInt("key-management.number-of-events-before-snapshot")
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
