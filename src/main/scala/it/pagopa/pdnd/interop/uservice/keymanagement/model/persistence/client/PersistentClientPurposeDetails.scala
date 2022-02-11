package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientPurposeDetails, ClientPurposeDetailsSeed}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClientPurposeDetails(id: UUID, state: PersistentClientComponentState) extends Persistent {

  def toApi: ClientPurposeDetails =
    ClientPurposeDetails(id = id, state = state.toApi)

}

object PersistentClientPurposeDetails {
  def fromSeed(uuidSupplier: UUIDSupplier)(seed: ClientPurposeDetailsSeed): PersistentClientPurposeDetails =
    PersistentClientPurposeDetails(id = uuidSupplier.get, state = PersistentClientComponentState.fromApi(seed.state))
}
