package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key

import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ENC, KeyUseEnum, SIG}

sealed trait PersistentKeyUse {
  def toRfcValue: String = this match {
    case Sig => "sig"
    case Enc => "enc"
  }
}

object PersistentKeyUse {
  def fromApi(value: KeyUseEnum): PersistentKeyUse = value match {
    case SIG => Sig
    case ENC => Enc
  }
}

case object Sig extends PersistentKeyUse
case object Enc extends PersistentKeyUse
