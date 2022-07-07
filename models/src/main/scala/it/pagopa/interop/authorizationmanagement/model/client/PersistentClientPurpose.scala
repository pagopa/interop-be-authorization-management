package it.pagopa.interop.authorizationmanagement.model.client

import java.util.UUID

final case class PersistentClientPurpose(id: UUID, statesChain: PersistentClientStatesChain)
