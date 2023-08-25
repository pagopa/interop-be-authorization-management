package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.authorizationmanagement.api.KeyApiMarshaller
import it.pagopa.interop.authorizationmanagement.model._
import spray.json._

object KeyApiMarshallerImpl extends KeyApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def fromEntityUnmarshallerKeyList: FromEntityUnmarshaller[Seq[Key]] =
    sprayJsonUnmarshaller[Seq[Key]]
  override implicit def toEntityMarshallerKeyarray: ToEntityMarshaller[Seq[Key]]        =
    sprayJsonMarshaller[Seq[Key]]
  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem]          = entityMarshallerProblem
  override implicit def toEntityMarshallerKey: ToEntityMarshaller[Key]                  = sprayJsonMarshaller[Key]

}
