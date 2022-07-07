package it.pagopa.interop.authorizationmanagement.model.client

import it.pagopa.interop.authorizationmanagement.model.client.PersistentClientPurposes.PersistentClientPurposes

import java.util.UUID

final case class PersistentClient(
  id: UUID,
  consumerId: UUID,
  name: String,
  purposes: PersistentClientPurposes,
  description: Option[String],
  relationships: Set[UUID],
  kind: PersistentClientKind
)
