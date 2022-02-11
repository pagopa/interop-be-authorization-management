package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.ClientPurposeDetails
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent

import java.util.UUID

final case class PersistentClientPurposeDetails(id: UUID, state: PersistentClientComponentState) extends Persistent {

  def toApi: ClientPurposeDetails =
    ClientPurposeDetails(id = id, state = state.toApi)

}
