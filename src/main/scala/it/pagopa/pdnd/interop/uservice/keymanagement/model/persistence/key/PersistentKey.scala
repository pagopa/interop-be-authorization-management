package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.ThumbprintCalculationError
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Keys, Persistent, ValidKey}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientKey, KeysResponse}
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

final case class PersistentKey(
  relationshipId: UUID,
  kid: String,
  encodedPem: String,
  algorithm: String,
  use: String,
  creationTimestamp: OffsetDateTime,
  deactivationTimestamp: Option[OffsetDateTime],
  status: KeyStatus
) extends Persistent

object PersistentKey {

  def toPersistentKey(validKey: ValidKey): Either[ThumbprintCalculationError, PersistentKey] = {
    for {
      kid <- KeyProcessor.calculateKid(validKey._2)
    } yield PersistentKey(
      relationshipId = validKey._1.relationshipId,
      kid = kid,
      encodedPem = validKey._1.key,
      algorithm = validKey._1.alg,
      use = validKey._1.use,
      creationTimestamp = OffsetDateTime.now(),
      deactivationTimestamp = None,
      status = Active
    )
  }

  @SuppressWarnings(
    Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing", "org.wartremover.warts.ToString")
  )
  def toAPIResponse(keys: Keys): Either[Throwable, KeysResponse] = {
    val processed = for {
      key <- keys.map { case (_, persistentKey) =>
        KeyProcessor
          .fromBase64encodedPEMToAPIKey(
            persistentKey.kid,
            persistentKey.encodedPem,
            persistentKey.use,
            persistentKey.algorithm
          )
          .map(ClientKey(_, persistentKey.status.toString, persistentKey.relationshipId))
      }
    } yield key

    processed.toSeq.sequence.map(elem => KeysResponse(keys = elem))
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def toAPI(persistentKey: PersistentKey): Either[Throwable, ClientKey] = {
    KeyProcessor
      .fromBase64encodedPEMToAPIKey(
        persistentKey.kid,
        persistentKey.encodedPem,
        persistentKey.use,
        persistentKey.algorithm
      )
      .map(ClientKey(_, persistentKey.status.toString, persistentKey.relationshipId))
  }

}
