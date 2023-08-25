package it.pagopa.interop.authorizationmanagement.model

import com.nimbusds.jose.jwk.JWK

package object persistence {
  type ValidKey = (KeySeed, JWK)
}
