package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Keys, ValidKey}
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor

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
  kid: String,
  encodedPem: String,
  creationTimestamp: OffsetDateTime,
  deactivationTimestamp: Option[OffsetDateTime],
  status: KeyStatus
) extends Persistent

object PersistentKey {

  def toPersistentKey(validKey: ValidKey): Either[Throwable, PersistentKey] = {
    for {
      kid <- KeyProcessor.calculateKid(validKey._2)
    } yield PersistentKey(
      kid = kid,
      encodedPem = validKey._1,
      creationTimestamp = OffsetDateTime.now(),
      deactivationTimestamp = None,
      status = Active
    )
  }

  def toAPIResponse(keys: Keys): Either[Throwable, KeysResponse] = {
    val processed = for {
      key <- keys.map(entry => KeyProcessor.fromBase64encodedPEMToAPIKey(entry._2.kid, entry._2.encodedPem))
    } yield key

    processed.toSeq.sequence.map(elem => KeysResponse(keys = elem))
  }

  def toAPI(key: PersistentKey): Either[Throwable, Key] = {
    KeyProcessor.fromBase64encodedPEMToAPIKey(key.kid, key.encodedPem)
  }

}
