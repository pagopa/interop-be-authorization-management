package it.pagopa.interop.authorizationmanagement.model.persistence.client

import it.pagopa.interop.authorizationmanagement.model.persistence.Persistent
import it.pagopa.interop.authorizationmanagement.model.{ClientAgreementDetails, ClientAgreementDetailsSeed}

import java.util.UUID

final case class PersistentClientAgreementDetails(
  eServiceId: UUID,
  consumerId: UUID,
  state: PersistentClientComponentState
) extends Persistent {

  def toApi: ClientAgreementDetails =
    ClientAgreementDetails(eserviceId = eServiceId, consumerId = consumerId, state = state.toApi)

}

object PersistentClientAgreementDetails {
  def fromSeed(seed: ClientAgreementDetailsSeed): PersistentClientAgreementDetails =
    PersistentClientAgreementDetails(
      eServiceId = seed.eserviceId,
      consumerId = seed.consumerId,
      state = PersistentClientComponentState.fromApi(seed.state)
    )
}
