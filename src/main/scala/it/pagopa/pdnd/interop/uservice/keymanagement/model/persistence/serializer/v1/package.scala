package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{KeyStatus, PersistentKey}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.events.KeysAddedV1
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.key.{
  KeyStatusV1,
  PersistentKeyEntryV1,
  PersistentKeyV1
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.serializer.v1.state.{StateEntryV1, StateV1}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Keys, KeysAdded, State}

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import cats.implicits._

package object v1 {

  type ErrorOr[A] = Either[Throwable, A]

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      for {
        clients <- state.keys
          .traverse[ErrorOr, (String, Keys)] {
            case client => {
              protoEntryToKey(client.keyEntries)
                .map(entry => client.clientId -> entry.toMap)
            }
          }
          .map(_.toMap)
      } yield State(clients)
    }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing", "org.wartremover.warts.OptionPartial"))
  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      for {
        clients <- state.keys.toSeq.traverse[ErrorOr, StateEntryV1] {
          case (client, keys) => {
            keyToEntry(keys).map(entries => StateEntryV1(client, entries))
          }
        }
      } yield StateV1(clients)

    }

  implicit def keysAddedV1PersistEventDeserializer: PersistEventDeserializer[KeysAddedV1, KeysAdded] = event => {
    protoEntryToKey(event.keys).map(keys => KeysAdded(clientId = event.clientId, keys = keys.toMap))
  }

  implicit def keysAddedV1PersistEventSerializer: PersistEventSerializer[KeysAdded, KeysAddedV1] = { event =>
    keyToEntry(event.keys).map(keys => KeysAddedV1(clientId = event.clientId, keys = keys))
  }

  private def keyToEntry(keys: Keys): ErrorOr[Seq[PersistentKeyEntryV1]] = {
    val entries = keys.map(entry => keyToProtobuf(entry._2).map(key => PersistentKeyEntryV1(entry._1, key))).toSeq
    entries.traverse[ErrorOr, PersistentKeyEntryV1](identity)
  }

  private def keyToProtobuf(key: PersistentKey): ErrorOr[PersistentKeyV1] =
    for {
      keyStatus <- KeyStatusV1
        .fromName(key.status.stringify)
        .toRight(new RuntimeException("Protobuf serialization failed"))
    } yield PersistentKeyV1(
      kty = key.kty,
      alg = key.alg,
      use = key.use,
      kid = key.kid,
      n = key.n,
      e = key.e,
      crv = key.crv,
      x = key.x,
      y = key.y,
      creationTimestamp = fromTime(key.creationTimestamp),
      deactivationTimestamp = key.deactivationTimestamp.map(fromTime),
      status = keyStatus
    )

  private def protoEntryToKey(keys: Seq[PersistentKeyEntryV1]): ErrorOr[Seq[(String, PersistentKey)]] = {
    val entries = keys.map(entry => protbufToKey(entry.value).map(key => (entry.keyId, key)))
    entries.traverse[ErrorOr, (String, PersistentKey)](identity)
  }

  private def protbufToKey(key: PersistentKeyV1): ErrorOr[PersistentKey] =
    for {
      keyStatus <- KeyStatus.fromText(key.status.name)
    } yield PersistentKey(
      kty = key.kty,
      alg = key.alg,
      use = key.use,
      kid = key.kid,
      n = key.n,
      e = key.e,
      crv = key.crv,
      x = key.x,
      y = key.y,
      creationTimestamp = toTime(key.creationTimestamp),
      deactivationTimestamp = key.deactivationTimestamp.map(toTime),
      status = keyStatus
    )

  private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

  def fromTime(timestamp: OffsetDateTime): String = timestamp.format(formatter)
  def toTime(timestamp: String): OffsetDateTime   = OffsetDateTime.parse(timestamp, formatter)
}
