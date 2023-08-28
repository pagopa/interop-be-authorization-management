package it.pagopa.interop.authorizationmanagement.api

import akka.cluster.sharding.typed.ClusterShardingSettings
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.SprayCommonFormats.{offsetDateTimeFormat, uuidFormat}
import it.pagopa.interop.commons.utils.errors.ServiceCode
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.reflect.classTag

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat6(Problem)

  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo]                 = jsonFormat3(OtherPrimeInfo)
  implicit val jwkKeyFormat: RootJsonFormat[JWKKey]                                 = customKeyFormat
  implicit val keyFormat: RootJsonFormat[Key]                                       = jsonFormat7(Key)
  implicit val keySeedFormat: RootJsonFormat[KeySeed]                               = jsonFormat6(KeySeed)
  implicit val keysFormat: RootJsonFormat[Keys]                                     = jsonFormat1(Keys)
  implicit val clientAgreementDetailsFormat: RootJsonFormat[ClientAgreementDetails] =
    jsonFormat4(ClientAgreementDetails)
  implicit val clientEServiceDetailsFormat: RootJsonFormat[ClientEServiceDetails]   = jsonFormat5(ClientEServiceDetails)
  implicit val clientPurposeDetailsFormat: RootJsonFormat[ClientPurposeDetails]     = jsonFormat3(ClientPurposeDetails)
  implicit val clientStatesChainFormat: RootJsonFormat[ClientStatesChain]           = jsonFormat4(ClientStatesChain)
  implicit val purposeFormat: RootJsonFormat[Purpose]                               = jsonFormat1(Purpose)

  implicit val eServiceDetailsSeedFormat: RootJsonFormat[ClientEServiceDetailsSeed]   =
    jsonFormat5(ClientEServiceDetailsSeed)
  implicit val agreementDetailsSeedFormat: RootJsonFormat[ClientAgreementDetailsSeed] =
    jsonFormat4(ClientAgreementDetailsSeed)
  implicit val purposeDetailsSeedFormat: RootJsonFormat[ClientPurposeDetailsSeed]     =
    jsonFormat3(ClientPurposeDetailsSeed)
  implicit val statesChainSeedFormat: RootJsonFormat[ClientStatesChainSeed] = jsonFormat3(ClientStatesChainSeed)
  implicit val purposeSeedFormat: RootJsonFormat[PurposeSeed]               = jsonFormat1(PurposeSeed)

  implicit val eServiceDetailsUpdateFormat: RootJsonFormat[ClientEServiceDetailsUpdate]                               =
    jsonFormat4(ClientEServiceDetailsUpdate)
  implicit val agreementDetailsUpdateFormat: RootJsonFormat[ClientAgreementDetailsUpdate]                             =
    jsonFormat2(ClientAgreementDetailsUpdate)
  implicit val clientAgreementAndEServiceDetailsUpdateFormat: RootJsonFormat[ClientAgreementAndEServiceDetailsUpdate] =
    jsonFormat6(ClientAgreementAndEServiceDetailsUpdate)
  implicit val purposeDetailsUpdateFormat: RootJsonFormat[ClientPurposeDetailsUpdate]                                 =
    jsonFormat2(ClientPurposeDetailsUpdate)

  implicit val clientSeedFormat: RootJsonFormat[ClientSeed]                  = jsonFormat6(ClientSeed)
  implicit val clientFormat: RootJsonFormat[Client]                          = jsonFormat8(Client)
  implicit val relationshipSeedFormat: RootJsonFormat[PartyRelationshipSeed] = jsonFormat1(PartyRelationshipSeed)

  implicit val keyWithClientFormat: RootJsonFormat[KeyWithClient] = jsonFormat2(KeyWithClient)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  implicit val serviceCode: ServiceCode = ServiceCode("006")

  def commander(id: String)(implicit sharding: ClusterSharding, settings: ClusterShardingSettings): EntityRef[Command] =
    sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(id, settings.numberOfShards))

  private def customKeyFormat: RootJsonFormat[JWKKey] = {
    val arrayFields = extractFieldNames(classTag[JWKKey])
    val Array(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21, p22) =
      arrayFields.map(elem => if (elem.equals("x5tS256")) "x5t#S256" else elem)
    jsonFormat(
      JWKKey.apply,
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
