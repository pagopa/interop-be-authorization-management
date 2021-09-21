package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient

import java.time.OffsetDateTime
import java.util.UUID

sealed trait Event extends Persistable

final case class KeysAdded(clientId: String, keys: Keys)                                             extends Event
final case class KeyDisabled(clientId: String, keyId: String, deactivationTimestamp: OffsetDateTime) extends Event
final case class KeyEnabled(clientId: String, keyId: String)                                         extends Event
final case class KeyDeleted(clientId: String, keyId: String, deactivationTimestamp: OffsetDateTime)  extends Event

final case class ClientAdded(client: PersistentClient) extends Event
final case class ClientDeleted(clientId: String)       extends Event

final case class RelationshipAdded(client: PersistentClient, relationshipId: UUID) extends Event
final case class RelationshipRemoved(clientId: String, relationshipId: String)     extends Event
