package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientEServiceDetails, ClientEServiceDetailsSeed}

import java.util.UUID

final case class PersistentClientEServiceDetails(
  eServiceId: UUID,
  state: PersistentClientComponentState,
  audience: String,
  voucherLifespan: Int
) extends Persistent {

  def toApi: ClientEServiceDetails =
    ClientEServiceDetails(
      eserviceId = eServiceId,
      state = state.toApi,
      audience = audience,
      voucherLifespan = voucherLifespan
    )

}

object PersistentClientEServiceDetails {
  def fromSeed(seed: ClientEServiceDetailsSeed): PersistentClientEServiceDetails =
    PersistentClientEServiceDetails(
      eServiceId = seed.eserviceId,
      state = PersistentClientComponentState.fromApi(seed.state),
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan
    )
}
