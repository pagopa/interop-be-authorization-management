package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.model.client.{
  PersistentClient,
  PersistentClientComponentState,
  PersistentClientStatesChain
}
import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes.Keys
import it.pagopa.interop.commons.queue.message.ProjectableEvent
import java.time.OffsetDateTime
import java.util.UUID

sealed trait Event extends Persistable with ProjectableEvent

final case class KeysAdded(clientId: String, keys: Keys)                                            extends Event
final case class KeyDeleted(clientId: String, keyId: String, deactivationTimestamp: OffsetDateTime) extends Event

final case class ClientAdded(client: PersistentClient) extends Event
final case class ClientDeleted(clientId: String)       extends Event

final case class RelationshipAdded(client: PersistentClient, relationshipId: UUID) extends Event
final case class RelationshipRemoved(clientId: String, relationshipId: String)     extends Event

final case class ClientPurposeAdded(clientId: String, statesChain: PersistentClientStatesChain) extends Event
final case class ClientPurposeRemoved(clientId: String, purposeId: String)                      extends Event

final case class EServiceStateUpdated(
  eServiceId: String,
  descriptorId: UUID,
  state: PersistentClientComponentState,
  audience: Seq[String],
  voucherLifespan: Int
) extends Event

final case class AgreementStateUpdated(
  eServiceId: String,
  consumerId: String,
  agreementId: UUID,
  state: PersistentClientComponentState
) extends Event

final case class AgreementAndEServiceStatesUpdated(
  eServiceId: String,
  descriptorId: UUID,
  consumerId: String,
  agreementId: UUID,
  agreementState: PersistentClientComponentState,
  eServiceState: PersistentClientComponentState,
  audience: Seq[String],
  voucherLifespan: Int
) extends Event

final case class PurposeStateUpdated(purposeId: String, versionId: UUID, state: PersistentClientComponentState)
    extends Event
