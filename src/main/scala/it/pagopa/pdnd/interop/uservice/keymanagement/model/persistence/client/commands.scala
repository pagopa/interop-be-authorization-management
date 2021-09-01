package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply

sealed trait ClientCommand

final case class AddClient(client: PersistentClient, replyTo: ActorRef[StatusReply[PersistentClient]])
    extends ClientCommand

case object Idle extends ClientCommand
