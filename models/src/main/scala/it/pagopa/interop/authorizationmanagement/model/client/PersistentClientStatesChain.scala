package it.pagopa.interop.authorizationmanagement.model.client

import java.util.UUID

final case class PersistentClientStatesChain(
  id: UUID,
  eservice: PersistentClientEServiceDetails,
  agreement: PersistentClientAgreementDetails,
  purpose: PersistentClientPurposeDetails
)
