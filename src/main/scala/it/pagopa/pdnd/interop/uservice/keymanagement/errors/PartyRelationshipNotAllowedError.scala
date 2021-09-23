package it.pagopa.pdnd.interop.uservice.keymanagement.errors

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{ClientId, RelationshipId}

final case class PartyRelationshipNotAllowedError(errors: Set[(RelationshipId, ClientId)])
    extends Throwable(
      errors
        .map(e => s"Party Relationship ${e._1} is not allowed to add keys to client ${e._2}")
        .mkString("[", ",", "]")
    )
