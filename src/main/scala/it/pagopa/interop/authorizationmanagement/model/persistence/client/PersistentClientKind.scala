package it.pagopa.interop.authorizationmanagement.model.persistence.client

import it.pagopa.interop.authorizationmanagement.model.ClientKind

sealed trait PersistentClientKind {
  def toApi: ClientKind = this match {
    case Consumer => ClientKind.CONSUMER
    case Api      => ClientKind.API
  }
}

case object Consumer extends PersistentClientKind
case object Api      extends PersistentClientKind

object PersistentClientKind {
  def fromApi(status: ClientKind): PersistentClientKind = status match {
    case ClientKind.CONSUMER => Consumer
    case ClientKind.API      => Api
  }
}
