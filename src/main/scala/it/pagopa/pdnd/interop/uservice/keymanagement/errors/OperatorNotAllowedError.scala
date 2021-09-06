package it.pagopa.pdnd.interop.uservice.keymanagement.errors

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{ClientId, OperatorId}

final case class OperatorNotAllowedError(errors: Set[(OperatorId, ClientId)])
  extends Throwable(errors.map(e => s"Operator ${e._1} is not allowed to add keys to client ${e._2}").mkString("[", ",", "]"))
