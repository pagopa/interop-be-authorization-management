package it.pagopa.interop.authorizationmanagement.model.client

import java.util.UUID

final case class PersistentClientPurposeDetails(purposeId: UUID, versionId: UUID, state: PersistentClientComponentState)
