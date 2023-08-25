package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxValidatedId, toTraverseOps}
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors.{InvalidKey, KeysAlreadyExist}
import it.pagopa.interop.authorizationmanagement.model.KeySeed
import it.pagopa.interop.authorizationmanagement.model.persistence.ValidKey
import it.pagopa.interop.authorizationmanagement.jwk.converter.KeyConverter

trait Validation {

  def validateKeys(keysSeed: Seq[KeySeed]): ValidatedNel[InvalidKey, Seq[ValidKey]] = keysSeed.traverse(validateKey)

  private def validateKey(keySeed: KeySeed): ValidatedNel[InvalidKey, ValidKey] = {
    val processedKey = for {
      jwk <- KeyConverter.fromBase64encodedPEM(keySeed.key)
      _   <- KeyConverter.publicKeyOnly(jwk)
    } yield jwk

    processedKey match {
      case Left(throwable) => InvalidKey(keySeed.key, throwable.getLocalizedMessage).invalidNel[ValidKey]
      case Right(jwk)      => (keySeed, jwk).validNel[InvalidKey]
    }
  }

  def validateWithCurrentKeys(
    inputPayload: Seq[ValidKey],
    currentKeys: LazyList[String]
  ): Either[KeysAlreadyExist, Seq[ValidKey]] = {

    val existingIds = inputPayload
      .map(key => key._2.computeThumbprint().toString)
      .filter(kid => currentKeys.contains(kid))

    existingIds match {
      case Nil => Right(inputPayload)
      case l   => Left(KeysAlreadyExist(l))
    }
  }

}
