package it.pagopa.interop.authorizationmanagement.model.persistence

import it.pagopa.interop.authorizationmanagement.model.client.PersistentClientComponentState._
import it.pagopa.interop.authorizationmanagement.model.client._
import it.pagopa.interop.authorizationmanagement.model.key._
import it.pagopa.interop.commons.utils.SprayCommonFormats._
import spray.json.DefaultJsonProtocol._
import spray.json._

object JsonFormats {

  implicit val pckFormat: RootJsonFormat[PersistentClientKind] =
    new RootJsonFormat[PersistentClientKind] {
      override def read(json: JsValue): PersistentClientKind = json match {
        case JsString("Consumer") => Consumer
        case JsString("Api")      => Api
        case other => deserializationError(s"Unable to deserialize json as a PersistentClientKind: $other")
      }

      override def write(obj: PersistentClientKind): JsValue = obj match {
        case Consumer => JsString("Consumer")
        case Api      => JsString("Api")
      }
    }

  implicit val pccsFormat: RootJsonFormat[PersistentClientComponentState] =
    new RootJsonFormat[PersistentClientComponentState] {
      override def read(json: JsValue): PersistentClientComponentState = json match {
        case JsString("Active")   => Active
        case JsString("Inactive") => Inactive
        case other => deserializationError(s"Unable to deserialize json as a PersistentClientComponentState: $other")
      }

      override def write(obj: PersistentClientComponentState): JsValue = obj match {
        case Active   => JsString("Active")
        case Inactive => JsString("Inactive")
      }
    }

  implicit val pkuFormat: RootJsonFormat[PersistentKeyUse] =
    new RootJsonFormat[PersistentKeyUse] {
      override def read(json: JsValue): PersistentKeyUse = json match {
        case JsString("Sig") => Sig
        case JsString("Enc") => Enc
        case other           => deserializationError(s"Unable to deserialize json as a PersistentKeyUse: $other")
      }

      override def write(obj: PersistentKeyUse): JsValue = obj match {
        case Sig => JsString("Sig")
        case Enc => JsString("Enc")
      }
    }

  implicit val pcedFormat: RootJsonFormat[PersistentClientEServiceDetails]  =
    jsonFormat5(PersistentClientEServiceDetails.apply)
  implicit val pcadFormat: RootJsonFormat[PersistentClientAgreementDetails] =
    jsonFormat4(PersistentClientAgreementDetails.apply)
  implicit val pcpdFormat: RootJsonFormat[PersistentClientPurposeDetails]   =
    jsonFormat3(PersistentClientPurposeDetails.apply)

  implicit val pcscFormat: RootJsonFormat[PersistentClientStatesChain] = jsonFormat4(PersistentClientStatesChain.apply)
  implicit val pcFormat: RootJsonFormat[PersistentClient]              = jsonFormat8(PersistentClient.apply)

  implicit val pkFormat: RootJsonFormat[PersistentKey] = jsonFormat7(PersistentKey.apply)

}
