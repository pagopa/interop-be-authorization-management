package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import cats.data.ValidatedNel
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Key

trait Validation {

  def validateKey(key: Key): ValidatedNel[String, Key] = ???

}
