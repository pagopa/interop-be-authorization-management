package it.pagopa.interop.be.authorizationmanagement.errors

import it.pagopa.interop.be.authorizationmanagement.model.persistence.{ClientId, RelationshipId}
import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

object KeyManagementErrors {
  final case class ClientAlreadyActiveError(clientId: String)
      extends ComponentError("0001", s"Client with id $clientId is already active")

  final case class ClientNotFoundError(clientId: String)
      extends ComponentError("0002", s"Client with id $clientId not found")

  final case class ClientAlreadySuspendedError(clientId: String)
      extends ComponentError("0003", s"Client with id $clientId is already suspended")

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

  final case class CreateKeysBadRequest(clientId: String)
      extends ComponentError("0007", s"Error while creating keys for client $clientId")
  final case class CreateKeysInvalid(clientId: String)
      extends ComponentError("0008", s"Error while creating keys for client $clientId - invalid")

  final case class ClientKeyNotFound(clientId: String, keyId: String)
      extends ComponentError("0009", s"Error while getting key $keyId for client $clientId - not found")
  final case class ClientKeysNotFound(clientId: String)
      extends ComponentError("0010", s"Error while getting keys for client $clientId - not found")

  final case class DeleteClientKeyNotFound(clientId: String, keyId: String)
      extends ComponentError("0011", s"Error while deleting key $keyId for client $clientId - not found")
  final case class EncodedClientKeyNotFound(clientId: String, keyId: String)
      extends ComponentError("0012", s"Error while getting encoded key $keyId for client $clientId - not found")

  final case object ClientAlreadyExisting extends ComponentError("0013", "Client already existing")
  final case class CreateClientError(consumerId: String)
      extends ComponentError("0014", s"Error creating client for Consumer $consumerId")
  final case class GetClientError(clientId: String)
      extends ComponentError("0015", s"Error while retrieving client $clientId")
  final case class GetClientServerError(clientId: String, reply: String)
      extends ComponentError("0016", s"Error while retrieving client $clientId : $reply")

  final case object ListClientErrors
      extends ComponentError("0018", "At least one parameter is required [ relationshipId, consumerId ]")

  final case class AddRelationshipError(relationshipId: String, clientId: String)
      extends ComponentError("0019", s"Error adding relationship $relationshipId to client $clientId")

  final case class DeleteClientError(clientId: String)
      extends ComponentError("0020", s"Error deleting client $clientId")

  final case class RemoveRelationshipError(relationshipId: String, clientId: String)
      extends ComponentError("0021", s"Error removing relationship $relationshipId to client $clientId")

  final case class ActivateClientError(clientId: String)
      extends ComponentError("0022", s"Error activating client $clientId")

  final case class SuspendClientError(clientId: String)
      extends ComponentError("0023", s"Error suspending client $clientId")

  final case class PurposeAlreadyExists(clientId: String, purposeId: String)
      extends ComponentError("0024", s"Client $clientId already contains Purpose $purposeId")

  final case class ClientPurposeAdditionError(clientId: String, purposeId: String)
      extends ComponentError("0025", s"Error adding Purpose $purposeId to Client $clientId")

  final case class ClientEServiceStateUpdateError(eServiceId: String)
      extends ComponentError("0026", s"Error updating EService $eServiceId state for all clients")

  final case class ClientAgreementStateUpdateError(agreementId: String)
      extends ComponentError("0027", s"Error updating Agreement $agreementId state for all clients")

  final case class ClientPurposeStateUpdateError(purposeId: String)
      extends ComponentError("0028", s"Error updating Purpose $purposeId state for all clients")

  final case class ClientWithPurposeNotFoundError(clientId: String, purposeId: String)
      extends ComponentError("0029", s"Not found a client for client=$clientId/purpose=$purposeId")

  final case class GenericError(error: String) extends ComponentError("0030", s"Something went wrong: $error")

}
