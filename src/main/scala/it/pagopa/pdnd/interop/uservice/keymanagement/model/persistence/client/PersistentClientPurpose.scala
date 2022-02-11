package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Purpose, PurposeSeed}

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
