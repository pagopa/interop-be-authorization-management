package it.pagopa.interop.authorizationmanagement.model.persistence.client

import it.pagopa.interop.authorizationmanagement.model.persistence.Persistent
import it.pagopa.interop.authorizationmanagement.model.{ClientEServiceDetails, ClientEServiceDetailsSeed}

import java.util.UUID

final case class PersistentClientEServiceDetails(
  eServiceId: UUID,
  descriptorId: UUID,
  state: PersistentClientComponentState,
  audience: Seq[String],
  voucherLifespan: Int
) extends Persistent {

  def toApi: ClientEServiceDetails =
    ClientEServiceDetails(
      eserviceId = eServiceId,
      descriptorId = descriptorId,
      state = state.toApi,
      audience = audience,
      voucherLifespan = voucherLifespan
    )

}

object PersistentClientEServiceDetails {
  def fromSeed(seed: ClientEServiceDetailsSeed): PersistentClientEServiceDetails =
    PersistentClientEServiceDetails(
      eServiceId = seed.eserviceId,
      descriptorId = seed.descriptorId,
      state = PersistentClientComponentState.fromApi(seed.state),
      audience = seed.audience,
      voucherLifespan = seed.voucherLifespan
    )
}
