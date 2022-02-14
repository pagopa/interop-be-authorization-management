package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientAgreementDetails, ClientAgreementDetailsSeed}

import java.util.UUID

final case class PersistentClientAgreementDetails(agreementId: UUID, state: PersistentClientComponentState)
    extends Persistent {

  def toApi: ClientAgreementDetails =
    ClientAgreementDetails(agreementId = agreementId, state = state.toApi)

}

object PersistentClientAgreementDetails {
  def fromSeed(seed: ClientAgreementDetailsSeed): PersistentClientAgreementDetails =
    PersistentClientAgreementDetails(
      agreementId = seed.agreementId,
      state = PersistentClientComponentState.fromApi(seed.state)
    )
}
