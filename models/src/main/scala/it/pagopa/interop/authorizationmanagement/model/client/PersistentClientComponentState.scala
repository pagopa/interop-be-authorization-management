package it.pagopa.interop.authorizationmanagement.model.client

sealed trait PersistentClientComponentState

object PersistentClientComponentState {
  case object Active   extends PersistentClientComponentState
  case object Inactive extends PersistentClientComponentState
}
