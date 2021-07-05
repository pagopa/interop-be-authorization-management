package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.ValidKey
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor

trait Validation {

  def validateKey(key: String): ValidatedNel[String, ValidKey] = {
    val processedKey = for {
      jwk <- KeyProcessor.fromBase64encodedPEM(key)
      _   <- KeyProcessor.publicKeyOnly(jwk)
      // _   <- KeyProcessor.usableJWK(jwk)
    } yield jwk

    processedKey match {
      case Left(throwable) => s"Key ${key} is invalid: ${throwable.getLocalizedMessage}".invalidNel[ValidKey]
      case Right(jwk)      => (key, jwk).validNel[String]
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  def validateKeys(keys: Seq[String]): ValidatedNel[String, Seq[ValidKey]] = {
    keys.traverse(validateKey)
  }

}
