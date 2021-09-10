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
      val clientId   = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val operatorId = UUID.randomUUID()

      createClient(clientId, eServiceId)

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
      val clientId   = UUID.randomUUID()
      val operatorId = UUID.randomUUID()

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
      val clientId    = UUID.randomUUID()
      val eServiceId  = UUID.randomUUID()
      val operatorId1 = UUID.randomUUID()
      val operatorId2 = UUID.randomUUID()

      createClient(clientId, eServiceId)
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
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()
      val operatorId = UUID.randomUUID()

      createClient(clientId1, eServiceId)
      createClient(clientId2, eServiceId)
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
