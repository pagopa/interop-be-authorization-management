package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.authorizationmanagement.api.MigrateApiMarshaller
import it.pagopa.interop.authorizationmanagement.model._
import spray.json._

object MigrateApiMarshallerImpl extends MigrateApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def fromEntityUnmarshallerUserSeed: FromEntityUnmarshaller[UserSeed] =
    sprayJsonUnmarshaller[UserSeed]

}
