package it.pagopa.pdnd.interop.uservice.keymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.keymanagement.model._
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.uuidFormat
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.reflect.classTag

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemFormat: RootJsonFormat[Problem] = jsonFormat3(Problem)

  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo]     = jsonFormat3(OtherPrimeInfo)
  implicit val keySeedFormat: RootJsonFormat[KeySeed]                   = jsonFormat4(KeySeed)
  implicit val keyFormat: RootJsonFormat[Key]                           = customKeyFormat
  implicit val clientKeyFormat: RootJsonFormat[ClientKey]               = jsonFormat2(ClientKey)
  implicit val encodedClientKeyFormat: RootJsonFormat[EncodedClientKey] = jsonFormat1(EncodedClientKey)
  implicit val keyResponseFormat: RootJsonFormat[KeysResponse]          = jsonFormat1(KeysResponse)

  implicit val clientSeedFormat: RootJsonFormat[ClientSeed]                  = jsonFormat5(ClientSeed)
  implicit val clientFormat: RootJsonFormat[Client]                          = jsonFormat8(Client)
  implicit val relationshipSeedFormat: RootJsonFormat[PartyRelationshipSeed] = jsonFormat1(PartyRelationshipSeed)

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
