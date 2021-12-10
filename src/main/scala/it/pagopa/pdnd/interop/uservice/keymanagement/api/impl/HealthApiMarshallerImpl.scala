package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.api.HealthApiMarshaller
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Problem

class HealthApiMarshallerImpl extends HealthApiMarshaller {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
