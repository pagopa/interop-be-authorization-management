package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.KeySeed
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.ValidKey
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor

trait Validation {

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  def validateKeys(keys: Seq[KeySeed]): ValidatedNel[String, Seq[ValidKey]] = {
    keys.traverse(validateKey)
  }

  private def validateKey(keySeed: KeySeed): ValidatedNel[String, ValidKey] = {
    val processedKey = for {
      jwk <- KeyProcessor.fromBase64encodedPEM(keySeed.key)
      _   <- KeyProcessor.publicKeyOnly(jwk)
      // _   <- KeyProcessor.usableJWK(jwk)
    } yield jwk

    processedKey match {
      case Left(throwable) => s"Key ${keySeed.key} is invalid: ${throwable.getLocalizedMessage}".invalidNel[ValidKey]
      case Right(jwk)      => (keySeed, jwk).validNel[String]
    }
  }

  def validateWithCurrentKeys(
    inputPayload: Seq[ValidKey],
    currentKeys: LazyList[String]
  ): ValidatedNel[String, Seq[ValidKey]] = {

    val existingIds = inputPayload
      .map(key => key._2.computeThumbprint().toString)
      .filter(kid => currentKeys.contains(kid))

    Option.when(existingIds.nonEmpty)(existingIds) match {
      case Some(existingIds) => s"These kids already exist: ${existingIds.mkString(", ")}".invalidNel[Seq[ValidKey]]
      case None              => inputPayload.validNel[String]
    }
  }

}
