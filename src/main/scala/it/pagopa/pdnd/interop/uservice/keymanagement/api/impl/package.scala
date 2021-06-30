package it.pagopa.pdnd.interop.uservice.keymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeyEntry, KeysResponse, Problem}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val problemFormat: RootJsonFormat[Problem]                 = jsonFormat3(Problem)
  implicit val keyFormat: RootJsonFormat[Key]                         = jsonFormat9(Key)
  implicit val keyEntryFormat: RootJsonFormat[KeyEntry]               = jsonFormat2(KeyEntry)
  implicit val keyResponseFormat: RootJsonFormat[KeysResponse] = jsonFormat2(KeysResponse)
}
