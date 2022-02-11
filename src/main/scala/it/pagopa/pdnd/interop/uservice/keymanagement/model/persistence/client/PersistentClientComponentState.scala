package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientComponentState

sealed trait PersistentClientComponentState {
  def toApi: ClientComponentState = this match {
    case PersistentClientComponentState.Active   => ClientComponentState.ACTIVE
    case PersistentClientComponentState.Inactive => ClientComponentState.INACTIVE
  }
}

object PersistentClientComponentState {
  def fromApi(value: ClientComponentState): PersistentClientComponentState = value match {
    case ClientComponentState.ACTIVE   => PersistentClientComponentState.Active
    case ClientComponentState.INACTIVE => PersistentClientComponentState.Inactive
  }

  case object Active   extends PersistentClientComponentState
  case object Inactive extends PersistentClientComponentState

}