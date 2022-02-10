package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientStateRing
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClientStateRing(id: UUID, state: PersistentRingState) extends Persistent {

  def toApi: ClientStateRing = ClientStateRing(id = id, state = state.toApi)

}
