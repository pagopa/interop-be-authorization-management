package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClient(
  id: UUID,
  eServiceId: UUID,
  name: String,
  description: Option[String],
  operators: Set[UUID]
) extends Persistent {

  def toApi: Client =
    Client(id = id, eServiceId = eServiceId, name = name, description = description, operators = operators)

}

object PersistentClient {

  def toPersistentClient(clientId: UUID, seed: ClientSeed): PersistentClient =
    PersistentClient(
      id = clientId,
      eServiceId = seed.eServiceId,
      name = seed.name,
      description = seed.description,
      operators = Set.empty[UUID]
    )

}
