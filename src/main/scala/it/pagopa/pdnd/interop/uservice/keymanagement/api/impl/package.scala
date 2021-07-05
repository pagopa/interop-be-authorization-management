package it.pagopa.pdnd.interop.uservice.keymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse, OtherPrimeInfo, Problem}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val problemFormat: RootJsonFormat[Problem]               = jsonFormat3(Problem)
  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo] = jsonFormat3(OtherPrimeInfo)
  implicit val keyFormat: RootJsonFormat[Key]                       = jsonFormat22(Key)
  implicit val keyResponseFormat: RootJsonFormat[KeysResponse]      = jsonFormat1(KeysResponse)
}
