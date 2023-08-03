package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client._
import it.pagopa.interop.commons.utils.service.UUIDSupplier

import java.util.UUID

object ClientAdapters {

  implicit class PersistentClientWrapper(private val p: PersistentClient) extends AnyVal {
    def toApi: Client =
      Client(
        id = p.id,
        consumerId = p.consumerId,
        name = p.name,
        purposes = p.purposes.map(_.toApi).map(Purpose),
        description = p.description,
        relationships = p.relationships,
        kind = p.kind.toApi,
        createdAt = p.createdAt
      )
  }

  implicit class PersistentClientObjectWrapper(private val p: PersistentClient.type) extends AnyVal {
    def toPersistentClient(clientId: UUID, seed: ClientSeed): PersistentClient =
      client.PersistentClient(
        id = clientId,
        consumerId = seed.consumerId,
        name = seed.name,
        purposes = Seq.empty,
        description = seed.description,
        relationships = seed.members.toSet,
        kind = PersistentClientKind.fromApi(seed.kind),
        createdAt = seed.createdAt
      )
  }

  implicit class PersistentClientStatesChainWrapper(private val p: PersistentClientStatesChain) extends AnyVal {
    def toApi: ClientStatesChain =
      ClientStatesChain(
        id = p.id,
        eservice = p.eService.toApi,
        agreement = p.agreement.toApi,
        purpose = p.purpose.toApi
      )
  }

  implicit class PersistentClientStatesChainObjectWrapper(private val p: PersistentClientStatesChain.type)
      extends AnyVal {

    def fromSeed(uuidSupplier: UUIDSupplier)(seed: ClientStatesChainSeed): PersistentClientStatesChain =
      client.PersistentClientStatesChain(
        id = uuidSupplier.get(),
        eService = PersistentClientEServiceDetails.fromSeed(seed.eservice),
        agreement = PersistentClientAgreementDetails.fromSeed(seed.agreement),
        purpose = PersistentClientPurposeDetails.fromSeed(seed.purpose)
      )
  }

  implicit class PersistentClientPurposeWrapper(private val p: PersistentClientPurpose) extends AnyVal {
    def toApi: Purpose = Purpose(states = p.statesChain.toApi)
  }

  implicit class PersistentClientPurposeObjectWrapper(private val p: PersistentClientPurpose.type) extends AnyVal {
    def fromSeed(uuidSupplier: UUIDSupplier)(seed: PurposeSeed): PersistentClientPurpose =
      PersistentClientPurpose(statesChain =
        PersistentClientStatesChain.fromSeed(uuidSupplier: UUIDSupplier)(seed.states)
      )
  }

  implicit class PersistentClientAgreementDetailsWrapper(private val p: PersistentClientAgreementDetails)
      extends AnyVal {
    def toApi: ClientAgreementDetails =
      ClientAgreementDetails(
        eserviceId = p.eServiceId,
        consumerId = p.consumerId,
        agreementId = p.agreementId,
        state = p.state.toApi
      )
  }

  implicit class PersistentClientAgreementDetailsObjectWrapper(private val p: PersistentClientAgreementDetails.type)
      extends AnyVal {
    def fromSeed(seed: ClientAgreementDetailsSeed): PersistentClientAgreementDetails =
      client.PersistentClientAgreementDetails(
        eServiceId = seed.eserviceId,
        consumerId = seed.consumerId,
        agreementId = seed.agreementId,
        state = PersistentClientComponentState.fromApi(seed.state)
      )
  }

  implicit class PersistentClientEServiceDetailsWrapper(private val p: PersistentClientEServiceDetails) extends AnyVal {
    def toApi: ClientEServiceDetails =
      ClientEServiceDetails(
        eserviceId = p.eServiceId,
        descriptorId = p.descriptorId,
        state = p.state.toApi,
        audience = p.audience,
        voucherLifespan = p.voucherLifespan
      )
  }

  implicit class PersistentClientEServiceDetailsObjectWrapper(private val p: PersistentClientEServiceDetails.type)
      extends AnyVal {
    def fromSeed(seed: ClientEServiceDetailsSeed): PersistentClientEServiceDetails =
      PersistentClientEServiceDetails(
        eServiceId = seed.eserviceId,
        descriptorId = seed.descriptorId,
        state = PersistentClientComponentState.fromApi(seed.state),
        audience = seed.audience,
        voucherLifespan = seed.voucherLifespan
      )
  }

  implicit class PersistentClientPurposeDetailsWrapper(private val p: PersistentClientPurposeDetails) extends AnyVal {
    def toApi: ClientPurposeDetails =
      ClientPurposeDetails(purposeId = p.purposeId, state = p.state.toApi, versionId = p.versionId)
  }

  implicit class PersistentClientPurposeDetailsObjectWrapper(private val p: PersistentClientPurposeDetails.type)
      extends AnyVal {
    def fromSeed(seed: ClientPurposeDetailsSeed): PersistentClientPurposeDetails =
      PersistentClientPurposeDetails(
        purposeId = seed.purposeId,
        versionId = seed.versionId,
        state = PersistentClientComponentState.fromApi(seed.state)
      )
  }

  implicit class PersistentClientKindWrapper(private val p: PersistentClientKind) extends AnyVal {
    def toApi: ClientKind = p match {
      case Consumer => ClientKind.CONSUMER
      case Api      => ClientKind.API
    }
  }

  implicit class PersistentClientKindObjectWrapper(private val p: PersistentClientKind.type) extends AnyVal {
    def fromApi(status: ClientKind): PersistentClientKind = status match {
      case ClientKind.CONSUMER => Consumer
      case ClientKind.API      => Api
    }
  }

  implicit class PersistentClientComponentStateWrapper(private val p: PersistentClientComponentState) extends AnyVal {
    def toApi: ClientComponentState = p match {
      case PersistentClientComponentState.Active   => ClientComponentState.ACTIVE
      case PersistentClientComponentState.Inactive => ClientComponentState.INACTIVE
    }
  }

  implicit class PersistentClientComponentStateObjectWrapper(private val p: PersistentClientComponentState.type)
      extends AnyVal {
    def fromApi(value: ClientComponentState): PersistentClientComponentState = value match {
      case ClientComponentState.ACTIVE   => PersistentClientComponentState.Active
      case ClientComponentState.INACTIVE => PersistentClientComponentState.Inactive
    }
  }

}
