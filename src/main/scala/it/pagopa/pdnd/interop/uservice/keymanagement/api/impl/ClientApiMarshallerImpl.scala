package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.api.ClientApiMarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, ClientSeed, Problem}
import spray.json._

class ClientApiMarshallerImpl extends ClientApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerClientSeed: FromEntityUnmarshaller[ClientSeed] =
    sprayJsonUnmarshaller[ClientSeed]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerClient: ToEntityMarshaller[Client] = sprayJsonMarshaller[Client]

  override implicit def toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]] =
    sprayJsonMarshaller[Seq[Client]]
}
