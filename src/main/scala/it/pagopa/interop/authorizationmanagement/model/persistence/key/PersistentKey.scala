package it.pagopa.interop.authorizationmanagement.model.persistence.key

import cats.implicits.toTraverseOps
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors.ThumbprintCalculationError
import it.pagopa.interop.authorizationmanagement.model.persistence.{Keys, Persistent, ValidKey}
import it.pagopa.interop.authorizationmanagement.model.{ClientKey, KeysResponse}
import it.pagopa.interop.authorizationmanagement.service.impl.KeyProcessor

import java.time.OffsetDateTime
import java.util.UUID

final case class PersistentKey(
  relationshipId: UUID,
  kid: String,
  name: String,
  encodedPem: String,
  algorithm: String,
  use: PersistentKeyUse,
  creationTimestamp: OffsetDateTime
) extends Persistent {
  def toApi: Either[Throwable, ClientKey] =
    KeyProcessor
      .fromBase64encodedPEMToAPIKey(kid, encodedPem, use, algorithm)
      .map(ClientKey(_, relationshipId, name, creationTimestamp))
}

object PersistentKey {

  def toPersistentKey(validKey: ValidKey): Either[ThumbprintCalculationError, PersistentKey] =
    for {
      kid <- KeyProcessor.calculateKid(validKey._2)
    } yield PersistentKey(
      relationshipId = validKey._1.relationshipId,
      kid = kid,
      name = validKey._1.name,
      encodedPem = validKey._1.key,
      algorithm = validKey._1.alg,
      use = PersistentKeyUse.fromApi(validKey._1.use),
      creationTimestamp = OffsetDateTime.now()
    )

  def toAPIResponse(keys: Keys): Either[Throwable, KeysResponse] =
    keys
      .map { case (_, persistentKey) => persistentKey.toApi }
      .toSeq
      .sequence
      .map(KeysResponse)

}
