package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import cats.syntax.either._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key
import it.pagopa.pdnd.interop.uservice.keymanagement.service.impl.KeyProcessor

trait Validation {

  def validateKey(key: Key): ValidatedNel[String, Key] = {
    KeyProcessor.validation(key).left.map(t => s"${key.kid} - ${t.getLocalizedMessage}").toValidatedNel
  }

  @SuppressWarnings(Array("org.wartremover.warts.Any", "org.wartremover.warts.Nothing"))
  def validateKeys(keys: Seq[Key]): ValidatedNel[String, Seq[Key]] = {
    keys.map(validateKey).sequence
  }

}
