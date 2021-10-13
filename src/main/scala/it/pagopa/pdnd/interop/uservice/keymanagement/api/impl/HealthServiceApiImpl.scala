package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.pdnd.interop.uservice.keymanagement.api.HealthApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Problem

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
class HealthServiceApiImpl extends HealthApiService {

  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = getStatus200(Problem(None, 200, "OK"))

}
