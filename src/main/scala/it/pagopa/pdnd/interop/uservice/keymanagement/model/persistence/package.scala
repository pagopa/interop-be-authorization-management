package it.pagopa.pdnd.interop.uservice.keymanagement.model

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey

package object persistence {
  type Keys = Map[String, PersistentKey]
}
