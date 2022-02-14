package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientStatesChain, ClientStatesChainSeed}

import java.util.UUID

final case class PersistentClientStatesChain(
  id: UUID,
  eService: PersistentClientEServiceDetails,
  agreement: PersistentClientAgreementDetails,
  purpose: PersistentClientPurposeDetails
) extends Persistent {

  def toApi: ClientStatesChain =
    ClientStatesChain(id = id, eservice = eService.toApi, agreement = agreement.toApi, purpose = purpose.toApi)

}

object PersistentClientStatesChain {
  def fromSeed(uuidSupplier: UUIDSupplier)(seed: ClientStatesChainSeed): PersistentClientStatesChain =
    PersistentClientStatesChain(
      id = uuidSupplier.get,
      eService = PersistentClientEServiceDetails.fromSeed(seed.eservice),
      agreement = PersistentClientAgreementDetails.fromSeed(seed.agreement),
      purpose = PersistentClientPurposeDetails.fromSeed(seed.purpose)
    )
}
