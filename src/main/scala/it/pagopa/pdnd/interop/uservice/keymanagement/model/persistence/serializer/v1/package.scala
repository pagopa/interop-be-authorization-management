package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import cats.implicits._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.{ClientStatus, PersistentClient}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{KeyStatus, PersistentKey}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.client.{
  ClientStatusV1,
  PersistentClientV1
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.events._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.key.{
  KeyStatusV1,
  PersistentKeyEntryV1,
  PersistentKeyV1
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.state.{
  StateClientsEntryV1,
  StateKeysEntryV1,
  StateV1
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try

package object v1 {

  type ErrorOr[A] = Either[Throwable, A]

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      for {
        keys <- state.keys
          .traverse[ErrorOr, (String, Keys)] { key =>
            protoEntryToKey(key.keyEntries)
              .map(entry => key.clientId -> entry.toMap)
          }
          .map(_.toMap)
        clients <- state.clients
          .traverse[ErrorOr, (String, PersistentClient)](protoEntryToClient)
          .map(_.toMap)
      } yield State(keys, clients)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.OptionPartial"))
  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      for {
        clients <- state.keys.toSeq.traverse[ErrorOr, StateKeysEntryV1] { case (client, keys) =>
          keyToEntry(keys).map(entries => StateKeysEntryV1(client, entries))
        }
      } yield StateV1(clients)

    }

  implicit def keysAddedV1PersistEventDeserializer: PersistEventDeserializer[KeysAddedV1, KeysAdded] = event => {
    protoEntryToKey(event.keys).map(keys => KeysAdded(clientId = event.clientId, keys = keys.toMap))
  }

  implicit def keysAddedV1PersistEventSerializer: PersistEventSerializer[KeysAdded, KeysAddedV1] = event => {
    keyToEntry(event.keys).map(keys => KeysAddedV1(clientId = event.clientId, keys = keys))
  }

  implicit def keyDeletedV1PersistEventDeserializer: PersistEventDeserializer[KeyDeletedV1, KeyDeleted] = event =>
    Right[Throwable, KeyDeleted](
      KeyDeleted(
        clientId = event.clientId,
        keyId = event.keyId,
        deactivationTimestamp = toTime(event.deactivationTimestamp)
      )
    )

  implicit def keyDeletedV1PersistEventSerializer: PersistEventSerializer[KeyDeleted, KeyDeletedV1] = event =>
    Right[Throwable, KeyDeletedV1](
      KeyDeletedV1(
        clientId = event.clientId,
        keyId = event.keyId,
        deactivationTimestamp = fromTime(event.deactivationTimestamp)
      )
    )

  implicit def keyDisabledV1PersistEventDeserializer: PersistEventDeserializer[KeyDisabledV1, KeyDisabled] = event =>
    Right[Throwable, KeyDisabled](
      KeyDisabled(
        clientId = event.clientId,
        keyId = event.keyId,
        deactivationTimestamp = toTime(event.deactivationTimestamp)
      )
    )

  implicit def keyDisabledV1PersistEventSerializer: PersistEventSerializer[KeyDisabled, KeyDisabledV1] = event =>
    Right[Throwable, KeyDisabledV1](
      KeyDisabledV1(
        clientId = event.clientId,
        keyId = event.keyId,
        deactivationTimestamp = fromTime(event.deactivationTimestamp)
      )
    )

  implicit def keyEnabledV1PersistEventDeserializer: PersistEventDeserializer[KeyEnabledV1, KeyEnabled] = event =>
    Right[Throwable, KeyEnabled](KeyEnabled(clientId = event.clientId, keyId = event.keyId))

  implicit def keyEnabledV1PersistEventSerializer: PersistEventSerializer[KeyEnabled, KeyEnabledV1] = event =>
    Right[Throwable, KeyEnabledV1](KeyEnabledV1(clientId = event.clientId, keyId = event.keyId))

  implicit def clientAddedV1PersistEventDeserializer: PersistEventDeserializer[ClientAddedV1, ClientAdded] = event =>
    protobufToClient(event.client).map(ClientAdded)

  implicit def clientAddedV1PersistEventSerializer: PersistEventSerializer[ClientAdded, ClientAddedV1] = event =>
    clientToProtobuf(event.client).map(client => ClientAddedV1(client = client))

  implicit def clientDeletedV1PersistEventDeserializer: PersistEventDeserializer[ClientDeletedV1, ClientDeleted] =
    event => Right[Throwable, ClientDeleted](ClientDeleted(clientId = event.clientId))

  implicit def clientDeletedV1PersistEventSerializer: PersistEventSerializer[ClientDeleted, ClientDeletedV1] = event =>
    Right[Throwable, ClientDeletedV1](ClientDeletedV1(clientId = event.clientId))

  implicit def clientActivatedV1PersistEventDeserializer: PersistEventDeserializer[ClientActivatedV1, ClientActivated] =
    event => Right[Throwable, ClientActivated](ClientActivated(clientId = event.clientId))

  implicit def clientActivatedV1PersistEventSerializer: PersistEventSerializer[ClientActivated, ClientActivatedV1] = event =>
    Right[Throwable, ClientActivatedV1](ClientActivatedV1(clientId = event.clientId))

  implicit def clientSuspendedV1PersistEventDeserializer: PersistEventDeserializer[ClientSuspendedV1, ClientSuspended] =
    event => Right[Throwable, ClientSuspended](ClientSuspended(clientId = event.clientId))

  implicit def clientSuspendedV1PersistEventSerializer: PersistEventSerializer[ClientSuspended, ClientSuspendedV1] = event =>
    Right[Throwable, ClientSuspendedV1](ClientSuspendedV1(clientId = event.clientId))

  implicit def relationshipAddedV1PersistEventDeserializer
    : PersistEventDeserializer[RelationshipAddedV1, RelationshipAdded] = event =>
    for {
      client         <- protobufToClient(event.client)
      relationshipId <- Try(UUID.fromString(event.relationshipId)).toEither
    } yield RelationshipAdded(client = client, relationshipId = relationshipId)

  implicit def relationshipAddedV1PersistEventSerializer
    : PersistEventSerializer[RelationshipAdded, RelationshipAddedV1] = event =>
    for {
      client <- clientToProtobuf(event.client)
    } yield RelationshipAddedV1(client = client, relationshipId = event.relationshipId.toString)

  implicit def relationshipRemovedV1PersistEventDeserializer
    : PersistEventDeserializer[RelationshipRemovedV1, RelationshipRemoved] =
    event =>
      Right[Throwable, RelationshipRemoved](
        RelationshipRemoved(clientId = event.clientId, relationshipId = event.relationshipId)
      )

  implicit def relationshipRemovedV1PersistEventSerializer
    : PersistEventSerializer[RelationshipRemoved, RelationshipRemovedV1] = event =>
    Right[Throwable, RelationshipRemovedV1](
      RelationshipRemovedV1(clientId = event.clientId, relationshipId = event.relationshipId)
    )

  private def keyToEntry(keys: Keys): ErrorOr[Seq[PersistentKeyEntryV1]] = {
    val entries = keys.map(entry => keyToProtobuf(entry._2).map(key => PersistentKeyEntryV1(entry._1, key))).toSeq
    entries.traverse[ErrorOr, PersistentKeyEntryV1](identity)
  }

  private def keyToProtobuf(key: PersistentKey): ErrorOr[PersistentKeyV1] =
    for {
      keyStatus <- KeyStatusV1
        .fromName(key.status.stringify)
        .toRight(new RuntimeException("Protobuf serialization failed"))
    } yield PersistentKeyV1(
      kid = key.kid,
      relationshipId = key.relationshipId.toString,
      encodedPem = key.encodedPem,
      algorithm = key.algorithm,
      use = key.use,
      creationTimestamp = fromTime(key.creationTimestamp),
      deactivationTimestamp = key.deactivationTimestamp.map(fromTime),
      status = keyStatus
    )

  private def clientToProtobuf(client: PersistentClient): ErrorOr[PersistentClientV1] =
    for {
      clientStatus <- ClientStatusV1
        .fromName(client.status.stringify)
        .toRight(new RuntimeException("Protobuf serialization failed"))
    } yield PersistentClientV1(
      id = client.id.toString,
      eServiceId = client.eServiceId.toString,
      consumerId = client.consumerId.toString,
      name = client.name,
      status = clientStatus,
      purposes = client.purposes,
      description = client.description,
      relationships = client.relationships.map(_.toString).toSeq
    )

  private def protoEntryToKey(keys: Seq[PersistentKeyEntryV1]): ErrorOr[Seq[(String, PersistentKey)]] = {
    val entries = keys.map(entry => protobufToKey(entry.value).map(key => (entry.keyId, key)))
    entries.traverse[ErrorOr, (String, PersistentKey)](identity)
  }

  private def protoEntryToClient(client: StateClientsEntryV1): ErrorOr[(String, PersistentClient)] =
    protobufToClient(client.client).map(pc => client.clientId -> pc)

  private def protobufToClient(client: PersistentClientV1): ErrorOr[PersistentClient] =
    for {
      clientId      <- Try(UUID.fromString(client.id)).toEither
      eServiceId    <- Try(UUID.fromString(client.eServiceId)).toEither
      consumerId    <- Try(UUID.fromString(client.consumerId)).toEither
      clientStatus  <- ClientStatus.fromText(client.status.name)
      relationships <- client.relationships.map(id => Try(UUID.fromString(id))).sequence.toEither
    } yield PersistentClient(
      id = clientId,
      eServiceId = eServiceId,
      consumerId = consumerId,
      name = client.name,
      status = clientStatus,
      purposes = client.purposes,
      description = client.description,
      relationships = relationships.toSet
    )

  private def protobufToKey(key: PersistentKeyV1): ErrorOr[PersistentKey] =
    for {
      keyStatus      <- KeyStatus.fromText(key.status.name)
      relationshipId <- Try(UUID.fromString(key.relationshipId)).toEither
    } yield PersistentKey(
      kid = key.kid,
      relationshipId = relationshipId,
      encodedPem = key.encodedPem,
      algorithm = key.algorithm,
      use = key.use,
      creationTimestamp = toTime(key.creationTimestamp),
      deactivationTimestamp = key.deactivationTimestamp.map(toTime),
      status = keyStatus
    )

  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def fromTime(timestamp: OffsetDateTime): String = timestamp.format(formatter)
  def toTime(timestamp: String): OffsetDateTime =
    OffsetDateTime.of(LocalDateTime.parse(timestamp, formatter), ZoneOffset.UTC)
}
