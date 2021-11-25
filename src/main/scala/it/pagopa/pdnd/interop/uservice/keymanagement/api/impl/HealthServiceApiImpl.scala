package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.keymanagement.api.HealthApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Problem

class HealthServiceApiImpl extends HealthApiService {

  override def getStatus()(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = getStatus200(Problem(None, 200, "OK"))

}
