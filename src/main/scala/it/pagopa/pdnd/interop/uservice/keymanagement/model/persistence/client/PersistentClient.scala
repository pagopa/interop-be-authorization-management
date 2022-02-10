package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClientPurpose.PersistentClientPurposes
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed}

import java.util.UUID

final case class PersistentClient(
  id: UUID,
  eServiceId: UUID,
  consumerId: UUID,
  name: String,
  state: PersistedClientState,
  purposes: PersistentClientPurposes,
  description: Option[String],
  relationships: Set[UUID]
) extends Persistent {

  def toApi: Client =
    Client(
      id = id,
      eServiceId = eServiceId,
      consumerId = consumerId,
      name = name,
      state = state.toApi,
      purposes = PersistentClientPurpose.toApi(purposes),
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
      state = Active,
      purposes = Map.empty,
      description = seed.description,
      relationships = Set.empty[UUID]
    )

}
