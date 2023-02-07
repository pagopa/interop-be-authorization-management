package it.pagopa.interop.authorizationmanagement.errors

import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes.{ClientId, RelationshipId}
import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object KeyManagementErrors {

  final case class ClientNotFoundError(clientId: String)
      extends ComponentError("0002", s"Client with id $clientId not found")

  final case class PartyRelationshipNotAllowedError(errors: Set[(RelationshipId, ClientId)])
      extends ComponentError(
        "0004",
        errors
          .map(e => s"Party Relationship ${e._1} is not allowed to add keys to client ${e._2}")
          .mkString("[", ",", "]")
      )

  final case class ThumbprintCalculationError(message: String)
      extends ComponentError("0005", s"Error while calculating keys thumbprints: $message")

  final case class PartyRelationshipNotFoundError(clientId: String, relationshipId: String)
      extends ComponentError(
        "0006",
        s"Party Relationship with id $relationshipId not found in Client with id $clientId"
      )

  final case class CreateKeysBadRequest(clientId: String, reasons: String)
      extends ComponentError("0007", s"Error while creating keys for client $clientId. Reason: $reasons")

  final case class ClientKeyNotFound(clientId: String, keyId: String)
      extends ComponentError("0009", s"Error while getting key $keyId for client $clientId - not found")

  final case class ClientAlreadyExisting(clientId: UUID)
      extends ComponentError("0013", s"Client $clientId already exists")

  final case class PurposeAlreadyExists(clientId: String, purposeId: String)
      extends ComponentError("0024", s"Client $clientId already contains Purpose $purposeId")

  final case class ClientWithPurposeNotFoundError(clientId: String, purposeId: String)
      extends ComponentError("0029", s"Not found a client for client=$clientId/purpose=$purposeId")

  final case class KeysAlreadyExist(existingIds: Seq[String])
      extends ComponentError("0030", s"Keys already exist: ${existingIds.mkString("[", ",", "]")}")

  final case class InvalidKey(kid: String, reason: String)
      extends ComponentError("0031", s"Key $kid is invalid. Reason: $reason")

  final case class InvalidKeys(errors: List[InvalidKey]) extends ComponentError("0032", errors.mkString("[", ",", "]"))

}
