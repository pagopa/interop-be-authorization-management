package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.model.client.{
  PersistentClient,
  PersistentClientComponentState,
  PersistentClientStatesChain
}
import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey
import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes.{ClientId, Keys}

import java.util.UUID

final case class State(keys: Map[ClientId, Keys], clients: Map[ClientId, PersistentClient]) extends Persistable {
  def deleteKey(clientId: String, keyId: String): State = keys.get(clientId) match {
    case Some(entries) =>
      copy(keys = keys + (clientId -> (entries - keyId)))
    case None          => this
  }

  def addKeys(clientId: String, addedKeys: Keys): State = {
    keys.get(clientId) match {
      case Some(entries) =>
        copy(keys = keys + (clientId -> (entries ++ addedKeys)))
      case None          => copy(keys = keys + (clientId -> addedKeys))
    }
  }

  def addClient(client: PersistentClient): State = {
    copy(clients = clients + (client.id.toString -> client))
  }

  def deleteClient(clientId: String): State =
    copy(clients = clients - clientId, keys = keys - clientId)

  def addRelationship(client: PersistentClient, relationshipId: UUID): State = {
    val updatedClient = client.copy(relationships = client.relationships + relationshipId)
    copy(clients = clients + (client.id.toString -> updatedClient))
  }

  def removeRelationship(clientId: String, relationshipId: String): State = {
    val updatedClients = clients.get(clientId) match {
      case Some(client) =>
        val updated = client.copy(relationships = client.relationships.filter(_.toString != relationshipId))
        clients + (clientId -> updated)
      case None         =>
        clients
    }

    copy(clients = updatedClients)
  }

  def getClientKeyById(clientId: String, keyId: String): Option[PersistentKey] =
    keys.get(clientId).flatMap(_.get(keyId))

  def addClientPurpose(clientId: String, statesChain: PersistentClientStatesChain): State = {
    clients.get(clientId) match {
      case Some(client) =>
        val purposes      = client.purposes :+ statesChain
        val updatedClient = client.copy(purposes = purposes)
        copy(clients = clients + (clientId -> updatedClient))
      case None         => this
    }
  }

  def removeClientPurpose(clientId: String, purposeId: String): State = {
    clients.get(clientId) match {
      case Some(client) =>
        val purposes      = client.purposes.filter(_.purpose.purposeId.toString != purposeId)
        val updatedClient = client.copy(purposes = purposes)
        copy(clients = clients + (clientId -> updatedClient))
      case None         => this
    }
  }

  def updateClientsByEService(
    eServiceId: String,
    descriptorId: UUID,
    state: PersistentClientComponentState,
    audience: Seq[String],
    voucherLifespan: Int
  ): State =
    updateClients(
      containsEService(eServiceId),
      states =>
        states.copy(eService =
          states.eService
            .copy(descriptorId = descriptorId, state = state, audience = audience, voucherLifespan = voucherLifespan)
        )
    )

  def updateClientsByAgreement(
    eServiceId: String,
    consumerId: String,
    agreementId: UUID,
    state: PersistentClientComponentState
  ): State =
    updateClients(
      containsAgreement(eServiceId, consumerId),
      states => states.copy(agreement = states.agreement.copy(agreementId = agreementId, state = state))
    )

  def updateClientsByPurpose(purposeId: String, versionId: UUID, state: PersistentClientComponentState): State =
    updateClients(
      containsPurpose(purposeId),
      states => states.copy(purpose = states.purpose.copy(versionId = versionId, state = state))
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

  def containsEService(eServiceId: String)(statesChain: PersistentClientStatesChain): Boolean =
    statesChain.eService.eServiceId.toString == eServiceId

  def containsAgreement(eServiceId: String, consumerId: String)(statesChain: PersistentClientStatesChain): Boolean =
    statesChain.agreement.eServiceId.toString == eServiceId && statesChain.agreement.consumerId.toString == consumerId

  def containsPurpose(purposeId: String)(statesChain: PersistentClientStatesChain): Boolean =
    statesChain.purpose.purposeId.toString == purposeId
}

object State {
  val empty: State = State(keys = Map.empty[String, Keys], clients = Map.empty[String, PersistentClient])
}
