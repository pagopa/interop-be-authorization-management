package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.RingState

sealed trait PersistentRingState {
  def toApi: RingState = this match {
    case PersistentRingState.Active   => RingState.ACTIVE
    case PersistentRingState.Inactive => RingState.INACTIVE
  }
}

object PersistentRingState {
  def fromApi(value: RingState): PersistentRingState = value match {
    case RingState.ACTIVE   => PersistentRingState.Active
    case RingState.INACTIVE => PersistentRingState.Inactive
  }

  case object Active   extends PersistentRingState
  case object Inactive extends PersistentRingState

}
