package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientState

sealed trait PersistedClientState {
  def toApi: ClientState = this match {
    case Active    => ClientState.ACTIVE
    case Suspended => ClientState.SUSPENDED
  }
}

object PersistedClientState {
  def fromApi(value: ClientState): PersistedClientState = value match {
    case ClientState.ACTIVE    => Active
    case ClientState.SUSPENDED => Suspended
  }
}

case object Active    extends PersistedClientState
case object Suspended extends PersistedClientState
