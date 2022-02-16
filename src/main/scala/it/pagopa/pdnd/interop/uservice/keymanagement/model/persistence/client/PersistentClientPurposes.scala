package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import cats.implicits.toTraverseOps
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Purpose

object PersistentClientPurposes {
  type PurposeId                = String
  type PersistentClientPurposes = Map[PurposeId, PersistentClientStatesChain]

  def toApi(persistent: PersistentClientPurposes): Either[Throwable, Seq[Purpose]] =
    persistent.toSeq.traverse { case (purposeId, statesChain) =>
      purposeId.toUUID.toEither.map(uuid => Purpose(purposeId = uuid, states = statesChain.toApi))
    }
}
