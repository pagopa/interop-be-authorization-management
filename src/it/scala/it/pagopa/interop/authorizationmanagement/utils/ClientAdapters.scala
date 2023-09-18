package it.pagopa.interop.authorizationmanagement.utils

import it.pagopa.interop.authorizationmanagement.model.client._
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientAdapters._
import it.pagopa.interop.authorizationmanagement.model._

object ClientAdapters {

  implicit class ClientWrapper(private val p: Client) extends AnyVal {
    def toPersistent: PersistentClient =
      PersistentClient(
        id = p.id,
        consumerId = p.consumerId,
        name = p.name,
        purposes = p.purposes.map(_.toPersistent),
        description = p.description,
        relationships = p.relationships,
        kind = PersistentClientKind.fromApi(p.kind),
        createdAt = p.createdAt
      )
  }

  implicit class PurposeWrapper(private val p: Purpose) extends AnyVal {
    def toPersistent: PersistentClientStatesChain =
      PersistentClientStatesChain(
        id = p.states.id,
        eService = p.states.eservice.toPersistent,
        agreement = p.states.agreement.toPersistent,
        purpose = p.states.purpose.toPersistent
      )
  }

  implicit class ClientEServiceDetailsWrapper(private val p: ClientEServiceDetails) extends AnyVal {
    def toPersistent: PersistentClientEServiceDetails =
      PersistentClientEServiceDetails(
        eServiceId = p.eserviceId,
        descriptorId = p.descriptorId,
        state = PersistentClientComponentState.fromApi(p.state),
        audience = p.audience,
        voucherLifespan = p.voucherLifespan
      )
  }

  implicit class ClientAgreementDetailsWrapper(private val p: ClientAgreementDetails) extends AnyVal {
    def toPersistent: PersistentClientAgreementDetails =
      PersistentClientAgreementDetails(
        eServiceId = p.eserviceId,
        consumerId = p.consumerId,
        agreementId = p.agreementId,
        state = PersistentClientComponentState.fromApi(p.state)
      )
  }

  implicit class ClientPurposeDetailsWrapper(private val p: ClientPurposeDetails) extends AnyVal {
    def toPersistent: PersistentClientPurposeDetails =
      PersistentClientPurposeDetails(
        purposeId = p.purposeId,
        versionId = p.versionId,
        state = PersistentClientComponentState.fromApi(p.state)
      )
  }

}
