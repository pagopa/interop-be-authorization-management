package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import java.time.OffsetDateTime

sealed trait Event extends Persistable

final case class KeysAdded(clientId: String, keys: Keys)                                             extends Event
final case class KeyDisabled(clientId: String, keyId: String, deactivationTimestamp: OffsetDateTime) extends Event
final case class KeyEnabled(clientId: String, keyId: String)                                         extends Event
final case class KeyDeleted(clientId: String, keyId: String, deactivationTimestamp: OffsetDateTime)  extends Event
