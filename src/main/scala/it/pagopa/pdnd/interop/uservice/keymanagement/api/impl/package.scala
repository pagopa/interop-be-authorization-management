package it.pagopa.pdnd.interop.uservice.keymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse, OtherPrimeInfo, Problem}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.reflect.classTag

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemFormat: RootJsonFormat[Problem]               = jsonFormat3(Problem)
  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo] = jsonFormat3(OtherPrimeInfo)
  implicit val keyFormat: RootJsonFormat[Key]                       = customKeyFormat
  implicit val keyResponseFormat: RootJsonFormat[KeysResponse]      = jsonFormat1(KeysResponse)

  private def customKeyFormat: RootJsonFormat[Key] = {
    val arrayFields = extractFieldNames(classTag[Key])
    val Array(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21, p22) =
      arrayFields.map(elem => if (elem.equals("x5tS256")) "x5t#S256" else elem)
    jsonFormat(
      Key.apply,
      p1,
      p2,
      p3,
      p4,
      p5,
      p6,
      p7,
      p8,
      p9,
      p10,
      p11,
      p12,
      p13,
      p14,
      p15,
      p16,
      p17,
      p18,
      p19,
      p20,
      p21,
      p22
    )
  }
}
