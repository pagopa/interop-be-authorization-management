package it.pagopa.pdnd.interop.uservice.keymanagement.errors

final case class ClientAlreadySuspendedError(clientId: String)
    extends Throwable(s"Client with id $clientId is already suspended")
