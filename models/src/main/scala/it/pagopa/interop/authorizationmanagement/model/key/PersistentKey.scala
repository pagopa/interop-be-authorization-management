package it.pagopa.interop.authorizationmanagement.model.key

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentKey(
  relationshipId: UUID,
  kid: String,
  name: String,
  encodedPem: String,
  algorithm: String,
  use: PersistentKeyUse,
  createdAt: OffsetDateTime
)
