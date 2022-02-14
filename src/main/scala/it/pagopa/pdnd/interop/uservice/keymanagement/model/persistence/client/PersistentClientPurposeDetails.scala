package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.Persistent
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientPurposeDetails, ClientPurposeDetailsSeed}

import java.util.UUID

final case class PersistentClientPurposeDetails(purposeId: UUID, state: PersistentClientComponentState)
    extends Persistent {

  def toApi: ClientPurposeDetails =
    ClientPurposeDetails(purposeId = purposeId, state = state.toApi)

}

object PersistentClientPurposeDetails {
  def fromSeed(seed: ClientPurposeDetailsSeed): PersistentClientPurposeDetails =
    PersistentClientPurposeDetails(
      purposeId = seed.purposeId,
      state = PersistentClientComponentState.fromApi(seed.state)
    )
}
