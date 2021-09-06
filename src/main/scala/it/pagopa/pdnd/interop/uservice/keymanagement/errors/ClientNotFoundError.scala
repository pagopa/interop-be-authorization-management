package it.pagopa.pdnd.interop.uservice.keymanagement.errors

final case class ClientNotFoundError(clientId: String) extends Throwable(s"Client with id $clientId not found")
