package it.pagopa.interop.authorizationmanagement.utils

import it.pagopa.interop.authorizationmanagement.model.ClientEServiceDetailsUpdate
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import it.pagopa.interop.commons.utils.SprayCommonFormats._

object JsonFormats extends DefaultJsonProtocol {
  implicit val ceduFormat: RootJsonFormat[ClientEServiceDetailsUpdate] = jsonFormat4(ClientEServiceDetailsUpdate)
}
