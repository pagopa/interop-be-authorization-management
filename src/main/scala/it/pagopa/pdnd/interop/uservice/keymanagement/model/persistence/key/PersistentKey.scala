package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.KeyManagementErrors.ThumbprintCalculationError
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Keys, Persistent, ValidKey}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientKey, KeysResponse}
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentKey(
  relationshipId: UUID,
  kid: String,
  encodedPem: String,
  algorithm: String,
  use: PersistentKeyUse,
  creationTimestamp: OffsetDateTime
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
      use = PersistentKeyUse.fromApi(validKey._1.use),
      creationTimestamp = OffsetDateTime.now()
    )
  }

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
          .map(ClientKey(_, persistentKey.relationshipId))
      }
    } yield key

    processed.toSeq.sequence.map(elem => KeysResponse(keys = elem))
  }

  def toAPI(persistentKey: PersistentKey): Either[Throwable, ClientKey] = {
    KeyProcessor
      .fromBase64encodedPEMToAPIKey(
        persistentKey.kid,
        persistentKey.encodedPem,
        persistentKey.use,
        persistentKey.algorithm
      )
      .map(ClientKey(_, persistentKey.relationshipId))
  }

}
