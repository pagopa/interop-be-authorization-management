package it.pagopa.interop.be.authorizationmanagement.model.persistence.client

import it.pagopa.interop.be.authorizationmanagement.model.{Purpose, PurposeSeed}
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier

import java.util.UUID

final case class PersistentClientPurpose(id: UUID, statesChain: PersistentClientStatesChain) {
  def toApi: Purpose = Purpose(purposeId = id, states = statesChain.toApi)
}

object PersistentClientPurpose {

  def fromSeed(uuidSupplier: UUIDSupplier)(seed: PurposeSeed): PersistentClientPurpose = PersistentClientPurpose(
    id = seed.purposeId,
    statesChain = PersistentClientStatesChain.fromSeed(uuidSupplier: UUIDSupplier)(seed.states)
  )
}
