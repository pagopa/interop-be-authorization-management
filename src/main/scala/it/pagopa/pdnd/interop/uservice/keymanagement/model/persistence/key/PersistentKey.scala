package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Keys
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeyEntry, KeysResponse}

import java.time.OffsetDateTime

sealed trait KeyStatus {
  def stringify: String = this match {
    case Active   => "Active"
    case Disabled => "Disabled"
    case Deleted  => "Deleted"
  }
}

object KeyStatus {
  def fromText(str: String): Either[Throwable, KeyStatus] = str match {
    case "Active"   => Right[Throwable, KeyStatus](Active)
    case "Disabled" => Right[Throwable, KeyStatus](Disabled)
    case "Deleted"  => Right[Throwable, KeyStatus](Deleted)
    case _          => Left[Throwable, KeyStatus](new RuntimeException("Deserialization from protobuf failed"))
  }
}

case object Active   extends KeyStatus
case object Disabled extends KeyStatus
case object Deleted  extends KeyStatus

sealed trait Persistent

final case class PersistentKey(
  kty: String,
  alg: String,
  use: String,
  kid: String,
  n: Option[String],
  e: Option[String],
  crv: Option[String],
  x: Option[String],
  y: Option[String],
  creationTimestamp: OffsetDateTime,
  deactivationTimestamp: Option[OffsetDateTime],
  status: KeyStatus
) extends Persistent

object PersistentKey {

  def toKeysMap(keys: Seq[Key]): Keys = keys.map(key => key.kid -> fromAPI(key)).toMap

  def toAPI(persistedKey: PersistentKey): Key = Key(
    kty = persistedKey.kty,
    alg = persistedKey.alg,
    use = persistedKey.use,
    kid = persistedKey.kid,
    n = persistedKey.n,
    e = persistedKey.e,
    crv = persistedKey.crv,
    x = persistedKey.x,
    y = persistedKey.y
  )

  def toAPIResponse(clientId: String, keys: Keys): KeysResponse = {
    KeysResponse(clientId = clientId, keys = keys.map(entry => KeyEntry(entry._1, toAPI(entry._2))).toSeq)
  }

  def fromAPI(key: Key): PersistentKey = PersistentKey(
    kty = key.kty,
    alg = key.alg,
    use = key.use,
    kid = key.kid,
    n = key.n,
    e = key.e,
    crv = key.crv,
    x = key.x,
    y = key.y,
    creationTimestamp = OffsetDateTime.now(),
    deactivationTimestamp = None,
    status = Active
  )
}
