package it.pagopa.interop.authorizationmanagement.model.client

import java.util.UUID

final case class PersistentClientStatesChain(
  id: UUID,
  eService: PersistentClientEServiceDetails,
  agreement: PersistentClientAgreementDetails,
  purpose: PersistentClientPurposeDetails
)
