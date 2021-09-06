package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse}

import java.util.UUID

sealed trait Command

final case class AddKeys(clientId: String, keys: Seq[ValidKey], replyTo: ActorRef[StatusReply[KeysResponse]])
    extends Command
final case class GetKeys(clientId: String, replyTo: ActorRef[StatusReply[KeysResponse]])           extends Command
final case class GetKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[Key]])      extends Command
final case class DisableKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
final case class EnableKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]])  extends Command
final case class DeleteKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]])  extends Command
final case class ListKid(from: Int, until: Int, replyTo: ActorRef[StatusReply[Seq[Kid]]])          extends Command

final case class AddClient(client: PersistentClient, replyTo: ActorRef[StatusReply[PersistentClient]]) extends Command
final case class GetClient(clientId: String, replyTo: ActorRef[StatusReply[PersistentClient]])         extends Command
final case class ListClients(
  from: Int,
  until: Int,
  agreementId: Option[String],
  operatorId: Option[String],
  replyTo: ActorRef[StatusReply[Seq[PersistentClient]]]
) extends Command
final case class AddOperator(clientId: String, operatorId: UUID, replyTo: ActorRef[StatusReply[PersistentClient]])
    extends Command
final case class DeleteClient(clientId: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
final case class RemoveOperator(clientId: String, operatorId: String, replyTo: ActorRef[StatusReply[Done]])
    extends Command

case object Idle extends Command
