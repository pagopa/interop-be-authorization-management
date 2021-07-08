package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{Active, Disabled, KeyStatus, PersistentKey}

import java.time.OffsetDateTime

/*
/partyId/keys
/partyId/key/{kid}
/agreement/keys GET
/agreement/key/kid GET ?
/agreement/key/kid  POST => creates a key for the agreemen
/agreement/key/kid  DELETE => creates a key for the agreement */

/*
    possible models
     indexes: Map[AgreementId, PartyId] //TODO evaluate about it
     agreements: Map[AgreementId, List[(Kid, PartyId)]]
 */

final case class State(keys: Map[PartyId, Keys]) extends Persistable {

  def enable(partyId: String, keyId: String): State = updateKey(partyId, keyId, Active, None)
  def disable(partyId: String, keyId: String, timestamp: OffsetDateTime): State =
    updateKey(partyId, keyId, Disabled, Some(timestamp))

  def delete(partyId: String, keyId: String): State = keys.get(partyId) match {
    case Some(entries) => {
      copy(keys = keys + (partyId -> (entries - keyId)))
    }
    case None => this
  }

  def addKeys(partyId: String, addedKeys: Keys): State = {
    keys.get(partyId) match {
      case Some(entries) => {
        copy(keys = keys + (partyId -> (entries ++ addedKeys)))
      }
      case None => copy(keys = keys + (partyId -> addedKeys))
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  def getClientActiveKeys(partyId: String): Option[Keys] = {
    for {
      keys <- keys.get(partyId)
      enabledKeys = keys.filter(key => key._2.status.equals(Active))
    } yield enabledKeys
  }
  def getActivePartyKeyById(partyId: String, keyId: String): Option[PersistentKey] =
    getClientActiveKeys(partyId).flatMap(_.get(keyId))

  private def updateKey(partyId: String, keyId: String, status: KeyStatus, timestamp: Option[OffsetDateTime]): State = {
    val keyToChange = keys.get(partyId).flatMap(_.get(keyId))

    keyToChange
      .fold(this)(key => {
        val updatedKey = key.copy(status = status, deactivationTimestamp = timestamp)
        addKeys(partyId, Map(updatedKey.kid -> updatedKey))
      })
  }

}

object State {
  val empty: State = State(keys = Map.empty[String, Keys])
}
