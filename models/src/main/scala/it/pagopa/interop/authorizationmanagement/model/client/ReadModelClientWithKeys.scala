package it.pagopa.interop.authorizationmanagement.model.client

import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey

final case class ReadModelClientWithKeys(client: PersistentClient, keys: Seq[PersistentKey])
