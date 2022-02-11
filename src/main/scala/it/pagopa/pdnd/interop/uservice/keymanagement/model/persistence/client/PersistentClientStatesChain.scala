package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientStatesChain
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
