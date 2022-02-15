package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.{
  PersistentClient,
  PersistentClientComponentState,
  PersistentClientStatesChain
}

import java.time.OffsetDateTime
import java.util.UUID

sealed trait Event extends Persistable

final case class KeysAdded(clientId: String, keys: Keys)                                            extends Event
final case class KeyDeleted(clientId: String, keyId: String, deactivationTimestamp: OffsetDateTime) extends Event

final case class ClientAdded(client: PersistentClient) extends Event
final case class ClientDeleted(clientId: String)       extends Event

final case class RelationshipAdded(client: PersistentClient, relationshipId: UUID) extends Event
final case class RelationshipRemoved(clientId: String, relationshipId: String)     extends Event

final case class ClientPurposeAdded(clientId: String, purposeId: UUID, statesChain: PersistentClientStatesChain)
    extends Event

final case class EServiceStateUpdated(
  eServiceId: String,
  state: PersistentClientComponentState,
  audience: Seq[String],
  voucherLifespan: Int
) extends Event

final case class AgreementStateUpdated(agreementId: String, state: PersistentClientComponentState) extends Event
final case class PurposeStateUpdated(purposeId: String, state: PersistentClientComponentState)     extends Event
