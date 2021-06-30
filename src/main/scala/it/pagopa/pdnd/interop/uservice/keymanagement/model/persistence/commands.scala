package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse}

sealed trait Command

final case class AddKeys(clientId: String, keys: Keys, replyTo: ActorRef[StatusReply[KeysResponse]])
    extends Command
final case class GetKeys(clientId: String, replyTo: ActorRef[StatusReply[KeysResponse]]) extends Command
final case class GetKey(clientId: String, keyId: String, replyTo: ActorRef[StatusReply[Key]])   extends Command

case object Idle extends Command
