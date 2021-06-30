package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key

final case class State(keys: Map[String, Keys]) extends Persistable {
  def addKeys(clientId: String, addedKeys: Keys): State = {
    keys.get(clientId) match {
      case Some(entries) => {
        copy(keys = keys + (clientId -> (entries ++ addedKeys)))
      }
      case None =>  copy(keys = keys + (clientId -> addedKeys))
    }
  }

  def getClientKeys(clientId: String): Option[Keys] = keys.get(clientId)
  def getClientKeyByKeyId(clientId: String, keyId: String): Option[Key] = {
    keys.get(clientId).flatMap(_.get(keyId))
  }
}

object State {
  val empty: State = State(keys = Map.empty[String, Keys])
}
