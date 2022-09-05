package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.authorizationmanagement.api.ClientApiMarshaller
import it.pagopa.interop.authorizationmanagement.model.{Client, ClientSeed, PartyRelationshipSeed, Problem}
import spray.json._

object ClientApiMarshallerImpl extends ClientApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerClientSeed: FromEntityUnmarshaller[ClientSeed] =
    sprayJsonUnmarshaller[ClientSeed]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerClient: ToEntityMarshaller[Client] = sprayJsonMarshaller[Client]

  override implicit def toEntityMarshallerClientarray: ToEntityMarshaller[Seq[Client]] =
    sprayJsonMarshaller[Seq[Client]]

  override implicit def fromEntityUnmarshallerPartyRelationshipSeed: FromEntityUnmarshaller[PartyRelationshipSeed] =
    sprayJsonUnmarshaller[PartyRelationshipSeed]
}
