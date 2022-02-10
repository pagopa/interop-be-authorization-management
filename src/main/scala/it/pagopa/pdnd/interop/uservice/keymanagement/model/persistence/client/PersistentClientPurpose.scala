package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import it.pagopa.pdnd.interop.uservice.keymanagement.model.Purpose

import java.util.UUID

object PersistentClientPurpose {
  type PurposeId                = UUID
  type PersistentClientPurposes = Map[PurposeId, PersistentClientStatesChain]

  def toApi(persistent: PersistentClientPurposes): Seq[Purpose] =
    persistent.map { case (purposeId, statesChain) =>
      Purpose(purposeId = purposeId, states = statesChain.toApi)
    }.toSeq
}
