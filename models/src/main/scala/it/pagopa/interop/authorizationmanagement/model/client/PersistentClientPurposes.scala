package it.pagopa.interop.authorizationmanagement.model.client

object PersistentClientPurposes {
  type PurposeId                = String
  type PersistentClientPurposes = Map[PurposeId, PersistentClientStatesChain]
}
