package it.pagopa.interop.authorizationmanagement.model.persistence.projection

import org.mongodb.scala.Document
import spray.json._

object Utils {

  def extractData[T: JsonReader](document: Document): T = {
    val fields = document.toJson().parseJson.asJsObject.getFields("data")
    fields match {
      // Failures are not handled to stop the projection and avoid the consumption of further events
      case data :: Nil => data.convertTo[T]
      case _           => throw new Exception(s"Unexpected number of fields ${fields.size}. Content: $fields")
    }
  }
}
