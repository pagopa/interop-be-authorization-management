package it.pagopa.pdnd.interop.uservice.keymanagement.model

import com.nimbusds.jose.jwk.JWK
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey

package object persistence {
  type ValidKey       = (KeySeed, JWK)
  type Kid            = String
  type ClientId       = String
  type RelationshipId = String
  type Keys           = Map[Kid, PersistentKey]
}
