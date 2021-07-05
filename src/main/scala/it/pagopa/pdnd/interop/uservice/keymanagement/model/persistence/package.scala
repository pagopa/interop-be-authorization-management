package it.pagopa.pdnd.interop.uservice.keymanagement.model

import com.nimbusds.jose.jwk.JWK
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey

package object persistence {
  type ValidKey = (String, JWK)
  type Kid      = String
  type Keys     = Map[Kid, PersistentKey]

}
