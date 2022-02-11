package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientStatesChain, ClientStatesChainSeed}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

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
      eService = PersistentClientEServiceDetails.fromSeed(uuidSupplier)(seed.eservice),
      agreement = PersistentClientAgreementDetails.fromSeed(uuidSupplier)(seed.agreement),
      purpose = PersistentClientPurposeDetails.fromSeed(uuidSupplier)(seed.purpose)
    )
}
