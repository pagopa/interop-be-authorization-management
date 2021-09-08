package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, KeysResponse}
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

  "Operator creation" should {
    "be successful on existing client" in {
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

    "fail if client does not exist" in {
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

  "Operator deletion" should {
    "be successful and remove keys" in {
      val clientId    = UUID.fromString("edc3d72e-85d7-4d6b-a0d1-a85b0aafda7c")
      val agreementId = UUID.fromString("5eb6143b-f9a9-4b1a-8292-0e3605fff601")
      val operatorId1 = UUID.fromString("b99edf43-c8ed-43cc-9945-509d9c6bb6a3")
      val operatorId2 = UUID.fromString("f8a7d939-741d-4f2a-bbea-2aeddeaa2a61")

      createClient(clientId, agreementId)
      addOperator(clientId, operatorId1)
      addOperator(clientId, operatorId2)
      createKey(clientId, operatorId1)
      val operator2Keys = createKey(clientId, operatorId2)

      val response =
        request(uri = s"$serviceURL/clients/$clientId/operators/$operatorId1", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      val clientRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId", method = HttpMethods.GET)
      val retrievedClient        = Await.result(Unmarshal(clientRetrieveResponse).to[Client], Duration.Inf)
      retrievedClient.operators shouldBe Set(operatorId2)

      val keysRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId/keys", method = HttpMethods.GET)
      val retrievedKeys        = Await.result(Unmarshal(keysRetrieveResponse).to[KeysResponse], Duration.Inf)
      retrievedKeys.keys shouldBe operator2Keys.keys
    }

    "be successful and don't remove same operator keys of different client" in {
      val clientId1   = UUID.fromString("8195991e-d660-4423-91cf-9a4e0603c25e")
      val clientId2   = UUID.fromString("1118da74-ffd3-4c98-999d-3322c59894ab")
      val agreementId = UUID.fromString("1f8b9bbf-2314-4c2f-87ef-5ce374798564")
      val operatorId  = UUID.fromString("f17119f1-925a-4adb-9d28-cdccf8a1d932")

      createClient(clientId1, agreementId)
      createClient(clientId2, agreementId)
      addOperator(clientId1, operatorId)
      addOperator(clientId2, operatorId)
      createKey(clientId1, operatorId)
      val client2Keys = createKey(clientId2, operatorId)

      val response =
        request(uri = s"$serviceURL/clients/$clientId1/operators/$operatorId", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      val clientRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId2", method = HttpMethods.GET)
      val retrievedClient        = Await.result(Unmarshal(clientRetrieveResponse).to[Client], Duration.Inf)
      retrievedClient.operators shouldBe Set(operatorId)

      val keysRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId2/keys", method = HttpMethods.GET)
      val retrievedKeys        = Await.result(Unmarshal(keysRetrieveResponse).to[KeysResponse], Duration.Inf)
      retrievedKeys.keys shouldBe client2Keys.keys
    }

  }

}
