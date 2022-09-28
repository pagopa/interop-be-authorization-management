package it.pagopa.interop.authorizationmanagement.utils

import it.pagopa.interop.authorizationmanagement.model.{
  ClientAgreementAndEServiceDetailsUpdate,
  ClientAgreementDetailsUpdate,
  ClientEServiceDetailsUpdate,
  ClientPurposeDetailsUpdate
}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import it.pagopa.interop.commons.utils.SprayCommonFormats._

object JsonFormats extends DefaultJsonProtocol {
  implicit val ceduFormat: RootJsonFormat[ClientEServiceDetailsUpdate]  = jsonFormat4(ClientEServiceDetailsUpdate)
  implicit val caduFormat: RootJsonFormat[ClientAgreementDetailsUpdate] = jsonFormat2(ClientAgreementDetailsUpdate)
  implicit val caseduFormat: RootJsonFormat[ClientAgreementAndEServiceDetailsUpdate] = jsonFormat6(
    ClientAgreementAndEServiceDetailsUpdate
  )
  implicit val cpduFormat: RootJsonFormat[ClientPurposeDetailsUpdate] = jsonFormat2(ClientPurposeDetailsUpdate)

}
