package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.api.KeyApiMarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Key, KeysResponse, Problem}
import spray.json._

class KeyApiMarshallerImpl extends KeyApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def fromEntityUnmarshallerKeyList: FromEntityUnmarshaller[Seq[Key]] =
    sprayJsonUnmarshaller[Seq[Key]]
  override implicit def toEntityMarshallerKey: ToEntityMarshaller[Key] = sprayJsonMarshaller[Key]
  override implicit def toEntityMarshallerKeysResponse: ToEntityMarshaller[KeysResponse] =
    sprayJsonMarshaller[KeysResponse]
  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
