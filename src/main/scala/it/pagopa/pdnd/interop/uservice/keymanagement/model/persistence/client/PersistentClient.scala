package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClientPurposes.PersistentClientPurposes
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed}

import java.util.UUID

final case class PersistentClient(
  id: UUID,
  consumerId: UUID,
  name: String,
  purposes: PersistentClientPurposes,
  description: Option[String],
  relationships: Set[UUID]
) extends Persistent {

  def toApi: Either[Throwable, Client] = PersistentClientPurposes.toApi(purposes).map { purposes =>
    Client(
      id = id,
      consumerId = consumerId,
      name = name,
      purposes = purposes,
      description = description,
      relationships = relationships
    )
  }

}

object PersistentClient {

  def toPersistentClient(clientId: UUID, seed: ClientSeed): PersistentClient =
    PersistentClient(
      id = clientId,
      consumerId = seed.consumerId,
      name = seed.name,
      purposes = Map.empty,
      description = seed.description,
      relationships = Set.empty[UUID]
    )

}
