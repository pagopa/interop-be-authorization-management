package it.pagopa.pdnd.interop.uservice.keymanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{
  Client,
  ClientKey,
  ClientSeed,
  Key,
  KeySeed,
  KeysResponse,
  PartyRelationshipSeed,
  OtherPrimeInfo,
  Problem
}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

import java.util.UUID
import scala.reflect.classTag
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val uuidFormat: JsonFormat[UUID] =
    new JsonFormat[UUID] {
      override def write(obj: UUID): JsValue = JsString(obj.toString)

      override def read(json: JsValue): UUID = json match {
        case JsString(s) =>
          Try(UUID.fromString(s)) match {
            case Success(result) => result
            case Failure(exception) =>
              deserializationError(s"could not parse $s as UUID", exception)
          }
        case notAJsString =>
          deserializationError(s"expected a String but got a ${notAJsString.compactPrint}")
      }
    }

  implicit val problemFormat: RootJsonFormat[Problem] = jsonFormat3(Problem)

  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo] = jsonFormat3(OtherPrimeInfo)
  implicit val keySeedFormat: RootJsonFormat[KeySeed]               = jsonFormat4(KeySeed)
  implicit val keyFormat: RootJsonFormat[Key]                       = customKeyFormat
  implicit val clientKeyFormat: RootJsonFormat[ClientKey]           = jsonFormat3(ClientKey)
  implicit val keyResponseFormat: RootJsonFormat[KeysResponse]      = jsonFormat1(KeysResponse)

  implicit val clientSeedFormat: RootJsonFormat[ClientSeed]                  = jsonFormat4(ClientSeed)
  implicit val clientFormat: RootJsonFormat[Client]                          = jsonFormat6(Client)
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
