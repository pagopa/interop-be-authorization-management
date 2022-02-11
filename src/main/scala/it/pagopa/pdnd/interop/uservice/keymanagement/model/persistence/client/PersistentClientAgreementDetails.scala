package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientAgreementDetails, ClientAgreementDetailsSeed}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClientAgreementDetails(id: UUID, state: PersistentClientComponentState) extends Persistent {

  def toApi: ClientAgreementDetails =
    ClientAgreementDetails(id = id, state = state.toApi)

}

object PersistentClientAgreementDetails {
  def fromSeed(uuidSupplier: UUIDSupplier)(seed: ClientAgreementDetailsSeed): PersistentClientAgreementDetails =
    PersistentClientAgreementDetails(id = uuidSupplier.get, state = PersistentClientComponentState.fromApi(seed.state))
}
