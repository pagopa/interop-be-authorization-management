package it.pagopa.interop.authorizationmanagement.model.client

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentClient(
  id: UUID,
  consumerId: UUID,
  name: String,
  purposes: Seq[PersistentClientStatesChain],
  description: Option[String],
  relationships: Set[UUID],
  kind: PersistentClientKind,
  createdAt: OffsetDateTime
)
