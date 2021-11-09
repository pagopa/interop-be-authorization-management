package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientStatus

sealed trait PersistedClientStatus {
  def toApi: ClientStatus = this match {
    case Active    => ClientStatus.ACTIVE
    case Suspended => ClientStatus.SUSPENDED
  }
}

object PersistedClientStatus {
  def fromApi(value: ClientStatus): PersistedClientStatus = value match {
    case ClientStatus.ACTIVE    => Active
    case ClientStatus.SUSPENDED => Suspended
  }
}

case object Active    extends PersistedClientStatus
case object Suspended extends PersistedClientStatus
