package it.pagopa.pdnd.interop.uservice.keymanagement.errors

final case class ClientAlreadyActiveError(clientId: String)
    extends Throwable(s"Client with id $clientId is already active")
