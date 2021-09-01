package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClient(id: UUID, agreementId: UUID, description: String) extends Persistent {

  def toApi: Client =
    Client(id = id, agreementId = agreementId, description = description)

}

object PersistentClient {

  def toPersistentClient(clientId: UUID, seed: ClientSeed): PersistentClient =
    PersistentClient(id = clientId, agreementId = seed.agreementId, description = seed.description)

}
