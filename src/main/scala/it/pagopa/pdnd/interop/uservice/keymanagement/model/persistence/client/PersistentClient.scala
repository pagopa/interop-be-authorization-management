package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClient(
  id: UUID,
  eServiceId: UUID,
  consumerId: UUID,
  name: String,
  objectives: String,
  description: Option[String],
  relationships: Set[UUID]
) extends Persistent {

  def toApi: Client =
    Client(
      id = id,
      eServiceId = eServiceId,
      consumerId = consumerId,
      name = name,
      objectives = objectives,
      description = description,
      relationships = relationships
    )

}

object PersistentClient {

  def toPersistentClient(clientId: UUID, seed: ClientSeed): PersistentClient =
    PersistentClient(
      id = clientId,
      eServiceId = seed.eServiceId,
      consumerId = seed.consumerId,
      name = seed.name,
      objectives = seed.objectives,
      description = seed.description,
      relationships = Set.empty[UUID]
    )

}
