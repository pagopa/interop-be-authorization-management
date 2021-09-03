package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse}

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

case object Idle extends Command
