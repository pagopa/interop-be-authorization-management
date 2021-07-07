package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.Done
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse}

sealed trait Command

final case class AddKeys(partyId: String, keys: Seq[ValidKey], replyTo: ActorRef[StatusReply[KeysResponse]])
    extends Command
final case class GetKeys(partyId: String, replyTo: ActorRef[StatusReply[KeysResponse]])           extends Command
final case class GetKey(partyId: String, keyId: String, replyTo: ActorRef[StatusReply[Key]])      extends Command
final case class DisableKey(partyId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]]) extends Command
final case class EnableKey(partyId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]])  extends Command
final case class DeleteKey(partyId: String, keyId: String, replyTo: ActorRef[StatusReply[Done]])  extends Command
final case class ListKid(from: Int, until: Int, replyTo: ActorRef[StatusReply[Seq[Kid]]])         extends Command

case object Idle extends Command
