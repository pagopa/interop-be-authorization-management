package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.model.client.{PersistentClient, PersistentClientStatesChain}
import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey
import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes.{ClientId, Keys}

final case class State(keys: Map[ClientId, Keys], clients: Map[ClientId, PersistentClient]) extends Persistable {
  def deleteKey(event: KeyDeleted): State = keys.get(event.clientId) match {
    case Some(entries) =>
      copy(keys = keys + (event.clientId -> (entries - event.keyId)))
    case None          => this
  }

  def addKeys(event: KeysAdded): State = {
    keys.get(event.clientId) match {
      case Some(entries) =>
        copy(keys = keys + (event.clientId -> (entries ++ event.keys)))
      case None          => copy(keys = keys + (event.clientId -> event.keys))
    }
  }

  def addClient(event: ClientAdded): State = {
    copy(clients = clients + (event.client.id.toString -> event.client))
  }

  def deleteClient(event: ClientDeleted): State =
    copy(clients = clients - event.clientId, keys = keys - event.clientId)

  def addRelationship(event: RelationshipAdded): State = {
    val updatedClient = event.client.copy(relationships = event.client.relationships + event.relationshipId)
    copy(clients = clients + (event.client.id.toString -> updatedClient))
  }

  def removeRelationship(event: RelationshipRemoved): State = {
    val updatedClients = clients.get(event.clientId) match {
      case Some(client) =>
        val updated = client.copy(relationships = client.relationships.filter(_.toString != event.relationshipId))
        clients + (event.clientId -> updated)
      case None         =>
        clients
    }

    copy(clients = updatedClients)
  }

  def getClientKeyById(clientId: String, keyId: String): Option[PersistentKey] =
    keys.get(clientId).flatMap(_.get(keyId))

  def addClientPurpose(event: ClientPurposeAdded): State = {
    clients.get(event.clientId) match {
      case Some(client) =>
        val purposes      = client.purposes :+ event.statesChain
        val updatedClient = client.copy(purposes = purposes)
        copy(clients = clients + (event.clientId -> updatedClient))
      case None         => this
    }
  }

  def removeClientPurpose(event: ClientPurposeRemoved): State = {
    clients.get(event.clientId) match {
      case Some(client) =>
        val purposes      = client.purposes.filter(_.purpose.purposeId.toString != event.purposeId)
        val updatedClient = client.copy(purposes = purposes)
        copy(clients = clients + (event.clientId -> updatedClient))
      case None         => this
    }
  }

  def updateClientsByEService(event: EServiceStateUpdated): State =
    updateClients(
      containsEService(event.eServiceId, event.descriptorId.toString),
      states =>
        states.copy(eService =
          states.eService
            .copy(
              descriptorId = event.descriptorId,
              state = event.state,
              audience = event.audience,
              voucherLifespan = event.voucherLifespan
            )
        )
    )

  def updateClientsByAgreement(event: AgreementStateUpdated): State =
    updateClients(
      containsAgreement(event.eServiceId, event.consumerId),
      states => states.copy(agreement = states.agreement.copy(agreementId = event.agreementId, state = event.state))
    )

  def updateClientsByAgreementAndEService(event: AgreementAndEServiceStatesUpdated): State = {
    updateClients(
      containsAgreement(event.eServiceId, event.consumerId),
      states =>
        states.copy(
          agreement = states.agreement.copy(agreementId = event.agreementId, state = event.agreementState),
          eService = states.eService.copy(
            descriptorId = event.descriptorId,
            state = event.eServiceState,
            audience = event.audience,
            voucherLifespan = event.voucherLifespan
          )
        )
    )
  }

  def updateClientsByPurpose(event: PurposeStateUpdated): State =
    updateClients(
      containsPurpose(event.purposeId),
      states => states.copy(purpose = states.purpose.copy(versionId = event.versionId, state = event.state))
    )

  private def updateClients(
    idComparison: PersistentClientStatesChain => Boolean,
    updateStates: PersistentClientStatesChain => PersistentClientStatesChain
  ): State = {
    val toUpdateClients = clients.filter { case (_, client) =>
      client.purposes.exists(idComparison)
    }

    def updateClient(client: PersistentClient): PersistentClient =
      client.copy(purposes = client.purposes.map {
        case statesChain if idComparison(statesChain) => updateStates(statesChain)
        case statesChain                              => statesChain
      })

    val updatedClients = toUpdateClients.map { case (clientId, client) => clientId -> updateClient(client) }

    copy(clients = clients ++ updatedClients)
  }

  def containsEService(eServiceId: String, descriptorId: String)(statesChain: PersistentClientStatesChain): Boolean =
    statesChain.eService.eServiceId.toString == eServiceId && statesChain.eService.descriptorId.toString == descriptorId

  def containsAgreement(eServiceId: String, consumerId: String)(statesChain: PersistentClientStatesChain): Boolean =
    statesChain.agreement.eServiceId.toString == eServiceId && statesChain.agreement.consumerId.toString == consumerId

  def containsPurpose(purposeId: String)(statesChain: PersistentClientStatesChain): Boolean =
    statesChain.purpose.purposeId.toString == purposeId
}

object State {
  val empty: State = State(keys = Map.empty[String, Keys], clients = Map.empty[String, PersistentClient])
}
