package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.authorizationmanagement.api.TokenGenerationApiMarshaller
import it.pagopa.interop.authorizationmanagement.model._
import spray.json._

object TokenGenerationApiMarshallerImpl
    extends TokenGenerationApiMarshaller
    with SprayJsonSupport
    with DefaultJsonProtocol {
  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem]             = entityMarshallerProblem
  override implicit def toEntityMarshallerKeyWithClient: ToEntityMarshaller[KeyWithClient] =
    sprayJsonMarshaller[KeyWithClient]
}
