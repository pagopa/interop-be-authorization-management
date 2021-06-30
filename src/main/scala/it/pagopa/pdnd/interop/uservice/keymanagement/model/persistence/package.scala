package it.pagopa.pdnd.interop.uservice.keymanagement.model

package object persistence {
  type Keys = Map[String, Key]
  def toKeysMap(keys: Seq[Key]): Keys = keys.map(key => key.kid -> key).toMap
}
