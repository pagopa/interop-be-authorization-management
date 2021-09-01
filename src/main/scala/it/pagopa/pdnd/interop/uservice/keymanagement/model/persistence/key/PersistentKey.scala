package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Keys, ValidKey}
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor

import java.time.OffsetDateTime
import java.util.UUID

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
  operatorId: UUID,
  kid: String,
  encodedPem: String,
  algorithm: String,
  use: String,
  creationTimestamp: OffsetDateTime,
  deactivationTimestamp: Option[OffsetDateTime],
  status: KeyStatus
) extends Persistent

object PersistentKey {

  def toPersistentKey(validKey: ValidKey): Either[Throwable, PersistentKey] = {
    for {
      kid <- KeyProcessor.calculateKid(validKey._2)
    } yield PersistentKey(
      operatorId = validKey._1.operatorId,
      kid = kid,
      encodedPem = validKey._1.key,
      algorithm = validKey._1.alg,
      use = validKey._1.use,
      creationTimestamp = OffsetDateTime.now(),
      deactivationTimestamp = None,
      status = Active
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  def toAPIResponse(keys: Keys): Either[Throwable, KeysResponse] = {
    val processed = for {
      key <- keys.map(entry =>
        KeyProcessor.fromBase64encodedPEMToAPIKey(entry._2.kid, entry._2.encodedPem, entry._2.use, entry._2.algorithm)
      )
    } yield key

    processed.toSeq.sequence.map(elem => KeysResponse(keys = elem))
  }

  def toAPI(key: PersistentKey): Either[Throwable, Key] = {
    KeyProcessor.fromBase64encodedPEMToAPIKey(key.kid, key.encodedPem, key.use, key.algorithm)
  }

}
