package it.pagopa.interop.be.authorizationmanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.be.authorizationmanagement.api.HealthApiMarshaller
import it.pagopa.interop.be.authorizationmanagement.model.Problem

object HealthApiMarshallerImpl extends HealthApiMarshaller {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
