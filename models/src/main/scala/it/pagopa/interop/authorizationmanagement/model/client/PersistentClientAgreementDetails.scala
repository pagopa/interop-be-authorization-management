package it.pagopa.interop.authorizationmanagement.model.client

import java.util.UUID

final case class PersistentClientAgreementDetails(
  eServiceId: UUID,
  consumerId: UUID,
  agreementId: UUID,
  state: PersistentClientComponentState
)
