package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.events.KeysAddedV1
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.key.{KeyEntryV1, KeyV1}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.state.{StateEntryV1, StateV1}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Keys, KeysAdded, State}

package object v1 {

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      val entries    = state.keys
      val entriesMap = entries.map(stateEntry => stateEntry.clientId -> protoEntryToKey(stateEntry.keyEntries)).toMap
      Right(State(entriesMap))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.OptionPartial"))
  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      val entries    = state.keys
      val entriesSeq = entries.map(entry => StateEntryV1(entry._1, keyToEntry(entry._2))).toSeq
      Right(StateV1(entriesSeq))
    }

  implicit def keysAddedV1PersistEventDeserializer: PersistEventDeserializer[KeysAddedV1, KeysAdded] = event =>
    Right[Throwable, KeysAdded](KeysAdded(clientId = event.clientId, keys = protoEntryToKey(event.keys)))

  implicit def keysAddedV1PersistEventSerializer: PersistEventSerializer[KeysAdded, KeysAddedV1] = { event =>
    Right[Throwable, KeysAddedV1](KeysAddedV1(clientId = event.clientId, keys = keyToEntry(event.keys)))
  }

  private def keyToEntry(keys: Keys): Seq[KeyEntryV1] =
    keys.map(entry => KeyEntryV1(entry._1, keyToProtobuf(entry._2))).toSeq
  private def keyToProtobuf(key: Key): KeyV1 =
    KeyV1(
      kty = key.kty,
      alg = key.alg,
      use = key.use,
      kid = key.kid,
      n = key.n,
      e = key.e,
      crv = key.crv,
      x = key.x,
      y = key.y
    )

  private def protoEntryToKey(keys: Seq[KeyEntryV1]): Keys =
    keys.map(entry => entry.keyId -> protbufToKey(entry.value)).toMap
  private def protbufToKey(key: KeyV1): Key =
    Key(
      kty = key.kty,
      alg = key.alg,
      use = key.use,
      kid = key.kid,
      n = key.n,
      e = key.e,
      crv = key.crv,
      x = key.x,
      y = key.y
    )
}
