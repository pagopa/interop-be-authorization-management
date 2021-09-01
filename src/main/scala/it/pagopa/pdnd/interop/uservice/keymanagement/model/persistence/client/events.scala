package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistable

sealed trait ClientEvent extends Persistable

final case class ClientAdded(client: PersistentClient) extends ClientEvent
