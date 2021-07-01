package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey

final case class State(keys: Map[String, Keys]) extends Persistable {
  def addKeys(clientId: String, addedKeys: Keys): State = {
    keys.get(clientId) match {
      case Some(entries) => {
        copy(keys = keys + (clientId -> (entries ++ addedKeys)))
      }
      case None => copy(keys = keys + (clientId -> addedKeys))
    }
  }

  def getClientKeys(clientId: String): Option[Keys] = keys.get(clientId)
  def getClientKeyByKeyId(clientId: String, keyId: String): Option[PersistentKey] = {
    keys.get(clientId).flatMap(_.get(keyId))
  }
  def containsKeys(clientId: String, keyIdentifiers: Seq[String]): Option[List[String]] = {
    val existingIds = keys
      .get(clientId)
      .map(_.keys.toList)
      .traverse(identity)
      .flatten
      .filter(kid => keyIdentifiers.contains(kid))

    Option.when(existingIds.nonEmpty)(existingIds)
  }
}

object State {
  val empty: State = State(keys = Map.empty[String, Keys])
}
