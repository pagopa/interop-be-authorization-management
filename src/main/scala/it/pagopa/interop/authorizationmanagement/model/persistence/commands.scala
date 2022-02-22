package it.pagopa.interop.authorizationmanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.interop.authorizationmanagement.model.persistence.client.{
  PersistentClient,
  PersistentClientComponentState,
  PersistentClientKind,
  PersistentClientPurpose
}
import it.pagopa.interop.authorizationmanagement.model.{ClientKey, EncodedClientKey, KeysResponse}

import java.util.UUID

sealed trait Command

final case class AddKeys(clientId: String, keys: Seq[ValidKey], replyTo: ActorRef[StatusReply[KeysResponse]])
    extends Command
final case class GetKeys(clientId: String, replyTo: ActorRef[StatusReply[KeysResponse]])            extends Command
final case class GetKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[ClientKey]]) extends Command
final case class GetEncodedKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[EncodedClientKey]])
    extends Command
final case class DeleteKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
final case class ListKid(from: Int, until: Int, replyTo: ActorRef[StatusReply[Seq[Kid]]])         extends Command

final case class AddClient(client: PersistentClient, replyTo: ActorRef[StatusReply[PersistentClient]]) extends Command
final case class GetClient(clientId: String, replyTo: ActorRef[StatusReply[PersistentClient]])         extends Command
final case class GetClientByPurpose(
  clientId: String,
  purposeId: String,
  replyTo: ActorRef[StatusReply[PersistentClient]]
) extends Command
final case class ListClients(
  from: Int,
  until: Int,
  relationshipId: Option[String],
  consumerId: Option[String],
  kind: Option[PersistentClientKind],
  replyTo: ActorRef[StatusReply[Seq[PersistentClient]]]
) extends Command
final case class AddRelationship(
  clientId: String,
  relationshipId: UUID,
  replyTo: ActorRef[StatusReply[PersistentClient]]
)                                                                                     extends Command
final case class DeleteClient(clientId: String, replyTo: ActorRef[StatusReply[Done]]) extends Command

final case class RemoveRelationship(clientId: String, relationshipId: String, replyTo: ActorRef[StatusReply[Done]])
    extends Command

final case class AddClientPurpose(
  clientId: String,
  persistentClientPurpose: PersistentClientPurpose,
  replyTo: ActorRef[StatusReply[PersistentClientPurpose]]
) extends Command

final case class RemoveClientPurpose(clientId: String, purposeId: String, replyTo: ActorRef[StatusReply[Unit]])
    extends Command

final case class UpdateEServiceState(
  eServiceId: String,
  state: PersistentClientComponentState,
  audience: Seq[String],
  voucherLifespan: Int,
  replyTo: ActorRef[StatusReply[Unit]]
) extends Command

final case class UpdateAgreementState(
  agreementId: String,
  state: PersistentClientComponentState,
  replyTo: ActorRef[StatusReply[Unit]]
) extends Command

final case class UpdatePurposeState(
  purposeId: String,
  state: PersistentClientComponentState,
  replyTo: ActorRef[StatusReply[Unit]]
) extends Command

case object Idle extends Command
