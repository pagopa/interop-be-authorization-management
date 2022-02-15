package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.{
  PersistentClient,
  PersistentClientPurpose
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientKey, EncodedClientKey, KeysResponse}

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
final case class GetClientByPurpose(clientId: String, purposeId: UUID, replyTo: ActorRef[StatusReply[PersistentClient]])
    extends Command
final case class ListClients(
  from: Int,
  until: Int,
  relationshipId: Option[String],
  consumerId: Option[String],
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

case object Idle extends Command
