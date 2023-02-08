package it.pagopa.interop.authorizationmanagement.model.persistence

import akka.Done
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import cats.implicits._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.authorizationmanagement.model.client.{PersistentClient, PersistentClientStatesChain}
import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier

import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{DurationInt, DurationLong}
import scala.language.postfixOps

object KeyPersistentBehavior {

  def commandHandler(
    shard: ActorRef[ClusterSharding.ShardCommand],
    context: ActorContext[Command],
    dateTimeSupplier: OffsetDateTimeSupplier
  ): (State, Command) => Effect[Event, State] = { (state, command) =>
    val idleTimeout = context.system.settings.config.getDuration("authorization-management.idle-timeout")
    context.setReceiveTimeout(idleTimeout.get(ChronoUnit.SECONDS) seconds, Idle)
    command match {
      case AddKeys(clientId, validKeys, replyTo) =>
        state.clients.get(clientId) match {
          case Some(client) =>
            validateRelationships(client, validKeys)
              .fold(error => commandError(replyTo, error), _ => addKeys(replyTo, clientId, validKeys))

          case None => commandError(replyTo, ClientNotFoundError(clientId))
        }

      case GetKey(clientId, keyId, replyTo) =>
        state.getClientKeyById(clientId, keyId) match {
          case Some(key) =>
            replyTo ! StatusReply.Success(key)
            Effect.none[Event, State]
          case None      => commandKeyNotFoundError(clientId, keyId, replyTo)
        }

      case GetKeyWithClient(clientId, keyId, replyTo) =>
        val clientAndKey = for {
          client <- state.clients.get(clientId)
          keys   <- state.keys.get(clientId)
          key    <- keys.get(keyId)
        } yield (client, key)

        clientAndKey match {
          case Some((client, key)) =>
            replyTo ! StatusReply.Success((client, key))
            Effect.none[Event, State]
          case None                => commandKeyNotFoundError(clientId, keyId, replyTo)
        }

      case DeleteKey(clientId, keyId, replyTo) =>
        state.getClientKeyById(clientId, keyId) match {
          case Some(_) =>
            Effect
              .persist(KeyDeleted(clientId, keyId, dateTimeSupplier.get()))
              .thenRun(_ => replyTo ! StatusReply.Success(Done))
          case None    => commandKeyNotFoundError(clientId, keyId, replyTo)
        }

      case GetKeys(clientId, replyTo) =>
        state.clients.get(clientId).fold(commandError(replyTo, ClientNotFoundError(clientId))) { _ =>
          val keys: Seq[PersistentKey] = state.keys.get(clientId).map(_.values.toSeq).getOrElse(Nil)
          replyTo ! StatusReply.Success(keys)
          Effect.none[Event, State]
        }

      case ListKid(from: Int, until: Int, replyTo) =>
        replyTo ! StatusReply.Success(state.keys.values.toSeq.slice(from, until).flatMap(_.keys))
        Effect.none[Event, State]

      // TODO Client commands should be in a separated behavior
      case AddClient(persistentClient, replyTo)    =>
        val client: Option[PersistentClient] = state.clients.get(persistentClient.id.toString)

        client
          .map(c => commandError(replyTo, ClientAlreadyExisting(c.id)))
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
          case None         => commandError(replyTo, ClientNotFoundError(clientId))
        }

      case GetClientByPurpose(clientId, purposeId, replyTo) =>
        state.clients
          .get(clientId)
          .find(client => client.purposes.exists(_.purpose.purposeId.toString == purposeId)) match {
          case Some(client) =>
            replyTo ! StatusReply.Success(client)
            Effect.none[Event, State]
          case None         => commandError(replyTo, ClientWithPurposeNotFoundError(clientId, purposeId))
        }

      case ListClients(from, to, relationshipId, consumerId, purposeId, kind, replyTo) =>
        val clientsByKind: Seq[PersistentClient] = kind
          .fold(state.clients.values)(k => state.clients.values.filter(_.kind == k))
          .toSeq

        val filteredClients: Seq[PersistentClient] = clientsByKind.filter { client =>
          relationshipId.forall(relationship => client.relationships.map(_.toString).contains(relationship)) &&
          consumerId.forall(_ == client.consumerId.toString) &&
          purposeId.forall(pId => client.purposes.exists(_.purpose.purposeId.toString == pId))
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
          _                <- persistentClient.relationships
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
        val purposeId                       = purpose.statesChain.purpose.purposeId
        val v: Either[ComponentError, Unit] = for {
          client <- state.clients.get(clientId).toRight(ClientNotFoundError(clientId))
          _      <- client.purposes
            .find(_.purpose.purposeId == purposeId)
            .toLeft(())
            .leftMap(_ => PurposeAlreadyExists(clientId, purposeId.toString))
        } yield ()

        v.fold(
          commandError(replyTo, _),
          _ =>
            Effect
              .persist(ClientPurposeAdded(clientId, purpose.statesChain))
              .thenRun((_: State) => replyTo ! StatusReply.Success(purpose))
        )

      case RemoveClientPurpose(clientId, purposeId, replyTo) =>
        state.clients
          .get(clientId)
          .toRight(ClientNotFoundError(clientId))
          .fold(
            commandError(replyTo, _),
            _ =>
              Effect
                .persist(ClientPurposeRemoved(clientId, purposeId))
                .thenRun((_: State) => replyTo ! StatusReply.Success(()))
          )

      case UpdateEServiceState(eServiceId, descriptorId, componentState, audience, voucherLifespan, replyTo) =>
        conditionalClientsStateUpdate(
          state,
          state.containsEService(eServiceId, descriptorId.toString),
          EServiceStateUpdated(eServiceId, descriptorId, componentState, audience, voucherLifespan),
          replyTo
        )

      case UpdateAgreementState(eServiceId, consumerId, agreementId, componentState, replyTo) =>
        conditionalClientsStateUpdate(
          state,
          state.containsAgreement(eServiceId, consumerId),
          AgreementStateUpdated(eServiceId, consumerId, agreementId, componentState),
          replyTo
        )

      case UpdateAgreementAndEServiceState(
            eServiceId,
            descriptorId,
            consumerId,
            agreementId,
            agreementState,
            eServiceState,
            audience,
            voucherLifespan,
            replyTo
          ) =>
        conditionalClientsStateUpdate(
          state,
          state.containsAgreement(eServiceId, consumerId),
          AgreementAndEServiceStatesUpdated(
            eServiceId = eServiceId,
            descriptorId = descriptorId,
            consumerId = consumerId,
            agreementId = agreementId,
            agreementState = agreementState,
            eServiceState = eServiceState,
            audience = audience,
            voucherLifespan = voucherLifespan
          ),
          replyTo
        )

      case UpdatePurposeState(purposeId, versionId, componentState, replyTo) =>
        conditionalClientsStateUpdate(
          state,
          state.containsPurpose(purposeId),
          PurposeStateUpdated(purposeId, versionId, componentState),
          replyTo
        )

      case Idle =>
        shard ! ClusterSharding.Passivate(context.self)
        context.log.debug(s"Passivate shard: ${shard.path.name}")
        Effect.none[Event, State]
    }
  }

  private def validateRelationships(
    client: PersistentClient,
    keys: Seq[PersistentKey]
  ): Either[PartyRelationshipNotAllowedError, Unit] = {
    val relationshipsNotInClient = keys.map(_.relationshipId).toSet -- client.relationships

    Either.cond(
      relationshipsNotInClient.isEmpty,
      (),
      PartyRelationshipNotAllowedError(
        relationshipsNotInClient.map(relationshipId => (relationshipId.toString, client.id.toString))
      )
    )
  }

  private def addKeys(
    replyTo: ActorRef[StatusReply[Seq[PersistentKey]]],
    clientId: String,
    keys: Seq[PersistentKey]
  ): Effect[Event, State] = {
    val mapKeys = keys.map(k => k.kid -> k).toMap
    Effect
      .persist(KeysAdded(clientId, mapKeys))
      .thenRun(_ => replyTo ! StatusReply.Success(keys))
  }

  private def conditionalClientsStateUpdate(
    state: State,
    condition: PersistentClientStatesChain => Boolean,
    event: Event,
    replyTo: ActorRef[StatusReply[Unit]]
  ): Effect[Event, State] =
    if (state.clients.exists { case (_, client) => client.purposes.exists(condition) })
      Effect
        .persist(event)
        .thenRun((_: State) => replyTo ! StatusReply.Success(()))
    else {
      replyTo ! StatusReply.Success(())
      Effect.none
    }

  private def commandKeyNotFoundError[T](
    clientId: String,
    keyId: String,
    replyTo: ActorRef[StatusReply[T]]
  ): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](ClientKeyNotFound(clientId, keyId))
    Effect.none[Event, State]
  }

  private def commandError[T](replyTo: ActorRef[StatusReply[T]], error: Throwable): Effect[Event, State] = {
    replyTo ! StatusReply.Error[T](error)
    Effect.none[Event, State]
  }

  val eventHandler: (State, Event) => State = (state, event) =>
    event match {
      case e: KeysAdded                         => state.addKeys(e)
      case e: KeyDeleted                        => state.deleteKey(e)
      case e: ClientAdded                       => state.addClient(e)
      case e: ClientDeleted                     => state.deleteClient(e)
      case e: RelationshipAdded                 => state.addRelationship(e)
      case e: RelationshipRemoved               => state.removeRelationship(e)
      case e: ClientPurposeAdded                => state.addClientPurpose(e)
      case e: ClientPurposeRemoved              => state.removeClientPurpose(e)
      case e: EServiceStateUpdated              => state.updateClientsByEService(e)
      case e: AgreementStateUpdated             => state.updateClientsByAgreement(e)
      case e: AgreementAndEServiceStatesUpdated => state.updateClientsByAgreementAndEService(e)
      case e: PurposeStateUpdated               => state.updateClientsByPurpose(e)
    }

  val TypeKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("interop-be-authorization-management-persistence")

  def apply(
    shard: ActorRef[ClusterSharding.ShardCommand],
    persistenceId: PersistenceId,
    dateTimeSupplier: OffsetDateTimeSupplier,
    projectionTag: String
  ): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.debug(s"Starting Key Shard ${persistenceId.id}")
      val numberOfEvents =
        context.system.settings.config.getInt("authorization-management.number-of-events-before-snapshot")
      EventSourcedBehavior[Command, Event, State](
        persistenceId = persistenceId,
        emptyState = State.empty,
        commandHandler = commandHandler(shard, context, dateTimeSupplier),
        eventHandler = eventHandler
      ).withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = numberOfEvents, keepNSnapshots = 1))
        .withTagger(_ => Set(projectionTag))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200 millis, 5 seconds, 0.1))
    }
  }
}
