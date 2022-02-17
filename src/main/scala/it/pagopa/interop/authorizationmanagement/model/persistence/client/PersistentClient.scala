package it.pagopa.interop.authorizationmanagement.model.persistence.client

import it.pagopa.interop.authorizationmanagement.model.persistence.Persistent
import it.pagopa.interop.authorizationmanagement.model.persistence.client.PersistentClientPurposes.PersistentClientPurposes
import it.pagopa.interop.authorizationmanagement.model.{Client, ClientSeed}

import java.util.UUID

final case class PersistentClient(
  id: UUID,
  consumerId: UUID,
  name: String,
  purposes: PersistentClientPurposes,
  description: Option[String],
  relationships: Set[UUID],
  kind: PersistentClientKind
) extends Persistent {

  def toApi: Either[Throwable, Client] = PersistentClientPurposes.toApi(purposes).map { purposes =>
    Client(
      id = id,
      consumerId = consumerId,
      name = name,
      purposes = purposes,
      description = description,
      relationships = relationships,
      kind = kind.toApi
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
      relationships = Set.empty[UUID],
      kind = PersistentClientKind.fromApi(seed.kind)
    )

}
