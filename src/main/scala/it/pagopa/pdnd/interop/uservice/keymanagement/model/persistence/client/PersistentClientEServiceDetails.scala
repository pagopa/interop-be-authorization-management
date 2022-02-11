package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientEServiceDetails
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClientEServiceDetails(
  id: UUID,
  state: PersistentClientComponentState,
  audience: String,
  voucherLifespan: Int
) extends Persistent {

  def toApi: ClientEServiceDetails =
    ClientEServiceDetails(id = id, state = state.toApi, audience = audience, voucherLifespan = voucherLifespan)

}
