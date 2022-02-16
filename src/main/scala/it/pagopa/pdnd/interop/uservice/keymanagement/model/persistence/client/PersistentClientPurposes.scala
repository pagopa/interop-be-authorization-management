package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.Purpose

import java.util.UUID

object PersistentClientPurposes {
  type PurposeId                = String
  type PersistentClientPurposes = Map[PurposeId, PersistentClientStatesChain]

  def toApi(persistent: PersistentClientPurposes): Seq[Purpose] =
    persistent.map { case (purposeId, statesChain) =>
      // This UUID conversion should be safe because this purposeId ca
      Purpose(purposeId = UUID.fromString(purposeId), states = statesChain.toApi)
    }.toSeq
}
