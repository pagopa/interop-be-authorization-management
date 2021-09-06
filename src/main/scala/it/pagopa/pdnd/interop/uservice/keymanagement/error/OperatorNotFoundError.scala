package it.pagopa.pdnd.interop.uservice.keymanagement.error

final case class OperatorNotFoundError(clientId: String, operatorId: String)
    extends Throwable(s"Operator with id $operatorId not found in Client with id $clientId")
