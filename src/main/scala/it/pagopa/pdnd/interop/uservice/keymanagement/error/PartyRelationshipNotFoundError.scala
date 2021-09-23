package it.pagopa.pdnd.interop.uservice.keymanagement.error

final case class PartyRelationshipNotFoundError(clientId: String, relationshipId: String)
    extends Throwable(s"Party Relationship with id $relationshipId not found in Client with id $clientId")
