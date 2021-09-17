package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.api.KeyApiMarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{ClientKey, KeySeed, KeysResponse, Problem}
import spray.json._

class KeyApiMarshallerImpl extends KeyApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {
  override implicit def fromEntityUnmarshallerKeySeedList: FromEntityUnmarshaller[Seq[KeySeed]] =
    sprayJsonUnmarshaller[Seq[KeySeed]]
  override implicit def toEntityMarshallerKeysResponse: ToEntityMarshaller[KeysResponse] =
    sprayJsonMarshaller[KeysResponse]
  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem]     = sprayJsonMarshaller[Problem]
  override implicit def toEntityMarshallerClientKey: ToEntityMarshaller[ClientKey] = sprayJsonMarshaller[ClientKey]
}
