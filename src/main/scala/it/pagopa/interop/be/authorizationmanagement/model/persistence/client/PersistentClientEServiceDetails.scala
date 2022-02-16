package it.pagopa.interop.be.authorizationmanagement.model.persistence.client

import it.pagopa.interop.be.authorizationmanagement.model.persistence.Persistent
import it.pagopa.interop.be.authorizationmanagement.model.{ClientEServiceDetails, ClientEServiceDetailsSeed}

import java.util.UUID

final case class PersistentClientEServiceDetails(
  eServiceId: UUID,
  state: PersistentClientComponentState,
  audience: Seq[String],
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
