package it.pagopa.interop.authorizationmanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.authorizationmanagement.api.HealthApiMarshaller
import it.pagopa.interop.authorizationmanagement.model.Problem

object HealthApiMarshallerImpl extends HealthApiMarshaller {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem
}
