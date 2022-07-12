package it.pagopa.interop.authorizationmanagement.model.client

import java.util.UUID

final case class PersistentClientEServiceDetails(
  eServiceId: UUID,
  descriptorId: UUID,
  state: PersistentClientComponentState,
  audience: Seq[String],
  voucherLifespan: Int
)
