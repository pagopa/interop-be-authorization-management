package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import it.pagopa.pdnd.interop.uservice.keymanagement.model.{KeyEntry, KeysResponse}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Keys

object Key {
  def toAPI(clientId: String, keys: Keys): KeysResponse = {
    KeysResponse(
      clientId = clientId, keys = keys.map(entry => KeyEntry(entry._1, entry._2)).toSeq
    )
  }
}
