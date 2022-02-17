package it.pagopa.interop.authorizationmanagement.model.persistence.key

import it.pagopa.interop.authorizationmanagement.model.KeyUse

sealed trait PersistentKeyUse {
  def toRfcValue: String = this match {
    case Sig => "sig"
    case Enc => "enc"
  }
}

object PersistentKeyUse {
  def fromApi(value: KeyUse): PersistentKeyUse = value match {
    case KeyUse.SIG => Sig
    case KeyUse.ENC => Enc
  }
}

case object Sig extends PersistentKeyUse
case object Enc extends PersistentKeyUse
