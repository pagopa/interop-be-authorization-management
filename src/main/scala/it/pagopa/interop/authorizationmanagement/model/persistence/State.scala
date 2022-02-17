package it.pagopa.interop.authorizationmanagement.model.persistence

import cats.implicits._
import it.pagopa.interop.authorizationmanagement.model.persistence.client.{
  PersistentClient,
  PersistentClientComponentState,
  PersistentClientStatesChain
}
import it.pagopa.interop.authorizationmanagement.model.persistence.key.PersistentKey

import java.util.UUID

final case class State(keys: Map[ClientId, Keys], clients: Map[ClientId, PersistentClient]) extends Persistable {
  def deleteKey(clientId: String, keyId: String): State = keys.get(clientId) match {
    case Some(entries) =>
      copy(keys = keys + (clientId -> (entries - keyId)))
    case None => this
  }

  def addKeys(clientId: String, addedKeys: Keys): State = {
    keys.get(clientId) match {
      case Some(entries) =>
        copy(keys = keys + (clientId -> (entries ++ addedKeys)))
      case None => copy(keys = keys + (clientId -> addedKeys))
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
      case None =>
        clients
    }

    val updatedKeys = keys.get(clientId) match {
      case Some(ks) =>
        val updated = ks.filter { case (_, key) => key.relationshipId.toString =!= relationshipId }
        keys + (clientId -> updated)
      case None =>
        keys
    }

    copy(clients = updatedClients, keys = updatedKeys)
  }

  def getClientKeyById(clientId: String, keyId: String): Option[PersistentKey] =
    keys.get(clientId).flatMap(_.get(keyId))

  def addClientPurpose(clientId: String, purposeId: String, statesChain: PersistentClientStatesChain): State = {
    clients.get(clientId) match {
      case Some(client) =>
        val purposes      = client.purposes + (purposeId -> statesChain)
        val updatedClient = client.copy(purposes = purposes)
        copy(clients = clients + (clientId -> updatedClient))
      case None => this
    }
  }

  def updateClientsByEService(
    eServiceId: String,
    state: PersistentClientComponentState,
    audience: Seq[String],
    voucherLifespan: Int
  ): State =
    updateClients(
      _.eService.eServiceId.toString == eServiceId,
      states =>
        states.copy(eService =
          states.eService.copy(state = state, audience = audience, voucherLifespan = voucherLifespan)
        )
    )

  def updateClientsByAgreement(agreementId: String, state: PersistentClientComponentState): State =
    updateClients(
      _.agreement.agreementId.toString == agreementId,
      states => states.copy(agreement = states.agreement.copy(state = state))
    )

  def updateClientsByPurpose(purposeId: String, state: PersistentClientComponentState): State =
    updateClients(
      _.purpose.purposeId.toString == purposeId,
      states => states.copy(purpose = states.purpose.copy(state = state))
    )

  private def updateClients(
    idComparison: PersistentClientStatesChain => Boolean,
    updateStates: PersistentClientStatesChain => PersistentClientStatesChain
  ): State = {
    val toUpdateClients = clients.filter { case (_, client) =>
      client.purposes.exists { case (_, statesChain) => idComparison(statesChain) }
    }

    def updateClient(client: PersistentClient): PersistentClient =
      client.copy(purposes = client.purposes.map {
        case (purposeId, statesChain) if idComparison(statesChain) =>
          purposeId -> updateStates(statesChain)
        case (purposeId, statesChain) =>
          purposeId -> statesChain
      })

    val updatedClients = toUpdateClients.map { case (clientId, client) => clientId -> updateClient(client) }

    copy(clients = clients ++ updatedClients)
  }

}

object State {
  val empty: State = State(keys = Map.empty[String, Keys], clients = Map.empty[String, PersistentClient])
}
