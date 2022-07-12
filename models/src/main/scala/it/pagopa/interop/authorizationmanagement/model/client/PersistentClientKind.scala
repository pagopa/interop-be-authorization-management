package it.pagopa.interop.authorizationmanagement.model.client

sealed trait PersistentClientKind
object PersistentClientKind
case object Consumer extends PersistentClientKind
case object Api      extends PersistentClientKind
