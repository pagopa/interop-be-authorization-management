package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.api.PurposeApiMarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Problem, Purpose, PurposeSeed}
import spray.json._

object PurposeApiMarshallerImpl extends PurposeApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def fromEntityUnmarshallerPurposeSeed: FromEntityUnmarshaller[PurposeSeed] =
    sprayJsonUnmarshaller[PurposeSeed]

  override implicit def toEntityMarshallerPurpose: ToEntityMarshaller[Purpose] = sprayJsonMarshaller[Purpose]
}
