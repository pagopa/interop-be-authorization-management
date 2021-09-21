package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{Active, Disabled, KeyStatus, PersistentKey}

import java.time.OffsetDateTime
import java.util.UUID
import cats.implicits._

/*
    possible models
     indexes: Map[AgreementId, clientId] //TODO evaluate about it
     agreements: Map[AgreementId, List[(Kid, clientId)]]
 */

final case class State(keys: Map[ClientId, Keys], clients: Map[ClientId, PersistentClient]) extends Persistable {
  def enable(clientId: String, keyId: String): State = updateKey(clientId, keyId, Active, None)
  def disable(clientId: String, keyId: String, timestamp: OffsetDateTime): State =
    updateKey(clientId, keyId, Disabled, Some(timestamp))

  def deleteKey(clientId: String, keyId: String): State = keys.get(clientId) match {
    case Some(entries) => {
      copy(keys = keys + (clientId -> (entries - keyId)))
    }
    case None => this
  }

  def addKeys(clientId: String, addedKeys: Keys): State = {
    keys.get(clientId) match {
      case Some(entries) => {
        copy(keys = keys + (clientId -> (entries ++ addedKeys)))
      }
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

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def getClientActiveKeys(clientId: String): Option[Keys] = {
    for {
      keys <- keys.get(clientId)
      enabledKeys = keys.filter(key => key._2.status.equals(Active))
    } yield enabledKeys
  }
  def getActiveClientKeyById(clientId: String, keyId: String): Option[PersistentKey] =
    getClientActiveKeys(clientId).flatMap(_.get(keyId))

  private def updateKey(
    clientId: String,
    keyId: String,
    status: KeyStatus,
    timestamp: Option[OffsetDateTime]
  ): State = {
    val keyToChange = keys.get(clientId).flatMap(_.get(keyId))

    keyToChange
      .fold(this)(key => {
        val updatedKey = key.copy(status = status, deactivationTimestamp = timestamp)
        addKeys(clientId, Map(updatedKey.kid -> updatedKey))
      })
  }

}

object State {
  val empty: State = State(keys = Map.empty[String, Keys], clients = Map.empty[String, PersistentClient])
}
