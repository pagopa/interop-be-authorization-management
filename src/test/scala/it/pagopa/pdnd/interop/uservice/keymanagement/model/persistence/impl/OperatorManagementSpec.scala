package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Client
import it.pagopa.pdnd.interop.uservice.keymanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
class OperatorManagementSpec
    extends ScalaTestWithActorTestKit(SpecConfiguration.config)
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

  override def beforeAll(): Unit = {
    startServer()
  }

  override def afterAll(): Unit = {
    shutdownServer()
    super.afterAll()
  }

  "Operator" should {
    "be created on existing client" in {
      val clientId    = UUID.fromString("29204cb0-f4a8-40a5-b447-8ba84ed988d4")
      val agreementId = UUID.fromString("c794f9a7-5d40-40d7-8fb9-af92d2b0356c")
      val operatorId  = UUID.fromString("7955e640-b2a1-4fe7-b0b4-e00045a83d1a")

      createClient(clientId, agreementId)

      val data = s"""{
                    |  "operatorId": "${operatorId.toString}"
                    |}""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/$clientId/operators", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.Created
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient.operators shouldBe Set(operatorId)
    }

    "fail creation if client does not exist" in {
      val clientId   = UUID.fromString("43c2fd14-4efb-4489-8f21-ac4977abee49")
      val operatorId = UUID.fromString("4fdfc95c-b687-4d5f-83a9-f0ce01037aea")

      val data = s"""{
                    |  "operatorId": "${operatorId.toString}"
                    |}""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/$clientId/operators", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.NotFound
    }
  }

}
