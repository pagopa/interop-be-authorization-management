package it.pagopa.interop.authorizationmanagement.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.errors.ComponentError
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.reflect.classTag

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo]     = jsonFormat3(OtherPrimeInfo)
  implicit val keySeedFormat: RootJsonFormat[KeySeed]                   = jsonFormat5(KeySeed)
  implicit val keyFormat: RootJsonFormat[Key]                           = customKeyFormat
  implicit val clientKeyFormat: RootJsonFormat[ClientKey]               = jsonFormat4(ClientKey)
  implicit val encodedClientKeyFormat: RootJsonFormat[EncodedClientKey] = jsonFormat1(EncodedClientKey)
  implicit val keyResponseFormat: RootJsonFormat[KeysResponse]          = jsonFormat1(KeysResponse)

  implicit val clientAgreementDetailsFormat: RootJsonFormat[ClientAgreementDetails] =
    jsonFormat4(ClientAgreementDetails)
  implicit val clientEServiceDetailsFormat: RootJsonFormat[ClientEServiceDetails]   = jsonFormat5(ClientEServiceDetails)
  implicit val clientPurposeDetailsFormat: RootJsonFormat[ClientPurposeDetails]     = jsonFormat3(ClientPurposeDetails)
  implicit val clientStatesChainFormat: RootJsonFormat[ClientStatesChain]           = jsonFormat4(ClientStatesChain)
  implicit val purposeFormat: RootJsonFormat[Purpose]                               = jsonFormat2(Purpose)

  implicit val eServiceDetailsSeedFormat: RootJsonFormat[ClientEServiceDetailsSeed]   =
    jsonFormat5(ClientEServiceDetailsSeed)
  implicit val agreementDetailsSeedFormat: RootJsonFormat[ClientAgreementDetailsSeed] =
    jsonFormat4(ClientAgreementDetailsSeed)
  implicit val purposeDetailsSeedFormat: RootJsonFormat[ClientPurposeDetailsSeed]     =
    jsonFormat3(ClientPurposeDetailsSeed)
  implicit val statesChainSeedFormat: RootJsonFormat[ClientStatesChainSeed] = jsonFormat3(ClientStatesChainSeed)
  implicit val purposeSeedFormat: RootJsonFormat[PurposeSeed]               = jsonFormat2(PurposeSeed)

  implicit val eServiceDetailsUpdateFormat: RootJsonFormat[ClientEServiceDetailsUpdate]   =
    jsonFormat4(ClientEServiceDetailsUpdate)
  implicit val agreementDetailsUpdateFormat: RootJsonFormat[ClientAgreementDetailsUpdate] =
    jsonFormat2(ClientAgreementDetailsUpdate)
  implicit val purposeDetailsUpdateFormat: RootJsonFormat[ClientPurposeDetailsUpdate]     =
    jsonFormat2(ClientPurposeDetailsUpdate)

  implicit val clientSeedFormat: RootJsonFormat[ClientSeed]                  = jsonFormat4(ClientSeed)
  implicit val clientFormat: RootJsonFormat[Client]                          = jsonFormat7(Client)
  implicit val relationshipSeedFormat: RootJsonFormat[PartyRelationshipSeed] = jsonFormat1(PartyRelationshipSeed)

  implicit val keyWithClientFormat: RootJsonFormat[KeyWithClient] = jsonFormat2(KeyWithClient)

  final val serviceErrorCodePrefix: String = "006"
  final val defaultProblemType: String     = "about:blank"

  def problemOf(httpError: StatusCode, error: ComponentError, defaultMessage: String = "Unknown error"): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultMessage)
        )
      )
    )

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
