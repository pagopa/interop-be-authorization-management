package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import java.time.OffsetDateTime

sealed trait Event extends Persistable

final case class KeysAdded(partyId: String, keys: Keys)                                             extends Event
final case class KeyDisabled(partyId: String, keyId: String, deactivationTimestamp: OffsetDateTime) extends Event
final case class KeyEnabled(partyId: String, keyId: String)                                         extends Event
final case class KeyDeleted(partyId: String, keyId: String, deactivationTimestamp: OffsetDateTime)  extends Event
