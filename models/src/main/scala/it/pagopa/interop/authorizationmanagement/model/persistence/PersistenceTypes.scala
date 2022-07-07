package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.model.key.PersistentKey

object PersistenceTypes {
  type Kid            = String
  type ClientId       = String
  type RelationshipId = String
  type Keys           = Map[Kid, PersistentKey]
}
