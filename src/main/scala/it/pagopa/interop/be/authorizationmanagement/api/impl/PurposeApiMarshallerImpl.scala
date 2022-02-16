package it.pagopa.interop.be.authorizationmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.be.authorizationmanagement.api.PurposeApiMarshaller
import it.pagopa.interop.be.authorizationmanagement.model._
import spray.json._

object PurposeApiMarshallerImpl extends PurposeApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def fromEntityUnmarshallerPurposeSeed: FromEntityUnmarshaller[PurposeSeed] =
    sprayJsonUnmarshaller[PurposeSeed]

  override implicit def toEntityMarshallerPurpose: ToEntityMarshaller[Purpose] = sprayJsonMarshaller[Purpose]

  override implicit def fromEntityUnmarshallerClientEServiceDetailsUpdate
    : FromEntityUnmarshaller[ClientEServiceDetailsUpdate] =
    sprayJsonUnmarshaller[ClientEServiceDetailsUpdate]

  override implicit def fromEntityUnmarshallerClientAgreementDetailsUpdate
    : FromEntityUnmarshaller[ClientAgreementDetailsUpdate] =
    sprayJsonUnmarshaller[ClientAgreementDetailsUpdate]

  override implicit def fromEntityUnmarshallerClientPurposeDetailsUpdate
    : FromEntityUnmarshaller[ClientPurposeDetailsUpdate] =
    sprayJsonUnmarshaller[ClientPurposeDetailsUpdate]

}
