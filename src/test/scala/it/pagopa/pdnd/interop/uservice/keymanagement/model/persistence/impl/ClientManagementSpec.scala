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
class ClientManagementSpec
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

  "Client" should {

    "be created successfully" in {
      val newClientUuid = UUID.randomUUID()
      (() => mockUUIDSupplier.get).expects().returning(newClientUuid).once()

      val eServiceUuid = UUID.randomUUID()
      val name         = "New Client 1"
      val description  = Some("New Client 1 description")

      val expected =
        Client(
          id = newClientUuid,
          eServiceId = eServiceUuid,
          name = name,
          description = description,
          operators = Set.empty
        )

      val data =
        s"""{
           |  "eServiceId": "${eServiceUuid.toString}",
           |  "name": "$name",
           |  "description": "${description.get}"
           |}""".stripMargin

      val response = request(uri = s"$serviceURL/clients", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.Created
      val createdClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      createdClient shouldBe expected

    }

    "be retrieved successfully" in {
      val clientUuid    = UUID.randomUUID()
      val eServiceUuid  = UUID.randomUUID()
      val createdClient = createClient(clientUuid, eServiceUuid)

      val response = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient shouldBe createdClient
    }

    "be deleted successfully" in {
      val clientUuid   = UUID.randomUUID()
      val eServiceUuid = UUID.randomUUID()
      val operatorUuid = UUID.randomUUID()

      createClient(clientUuid, eServiceUuid)
      addOperator(clientUuid, operatorUuid)
      createKey(clientUuid, operatorUuid)

      val deleteResponse = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.DELETE)
      deleteResponse.status shouldBe StatusCodes.NoContent

      val retrieveClientResponse = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.GET)
      retrieveClientResponse.status shouldBe StatusCodes.NotFound

      val retrieveKeysResponse = request(uri = s"$serviceURL/clients/$clientUuid/keys", method = HttpMethods.GET)
      retrieveKeysResponse.status shouldBe StatusCodes.NotFound
    }

    "fail on non-existing client id" in {
      val deleteResponse = request(uri = s"$serviceURL/clients/non-existing-id", method = HttpMethods.DELETE)
      deleteResponse.status shouldBe StatusCodes.NotFound
    }

  }

  "Client list" should {
    "correctly filter by E-Service id" in {
      val clientId1   = UUID.randomUUID()
      val clientId2   = UUID.randomUUID()
      val clientId3   = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val eServiceId3 = UUID.randomUUID()

      val client1 = createClient(clientId1, eServiceId1)
      val client2 = createClient(clientId2, eServiceId1)
      createClient(clientId3, eServiceId3)

      val response = request(uri = s"$serviceURL/clients?eServiceId=$eServiceId1", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size shouldBe 2
      retrievedClients should contain only (client1, client2)

    }

    "correctly filter by operator id" in {
      val clientId1   = UUID.randomUUID()
      val clientId2   = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()
      val operatorId1 = UUID.randomUUID()
      val operatorId2 = UUID.randomUUID()

      createClient(clientId1, eServiceId1)
      createClient(clientId2, eServiceId2)

      addOperator(clientId1, operatorId1)
      addOperator(clientId2, operatorId2)

      // List clients
      val response = request(uri = s"$serviceURL/clients?operatorId=$operatorId1", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size shouldBe 1
      retrievedClients.head.id shouldBe clientId1

    }

    "correctly paginate elements" in {
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val clientId3  = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()

      createClient(clientId1, eServiceId)
      createClient(clientId2, eServiceId)
      createClient(clientId3, eServiceId)

      // First page
      val offset1 = 0
      val limit1  = 2

      val response1 = request(
        uri = s"$serviceURL/clients?eServiceId=$eServiceId&offset=$offset1&limit=$limit1",
        method = HttpMethods.GET
      )

      response1.status shouldBe StatusCodes.OK
      val retrievedClients1 = Await.result(Unmarshal(response1).to[Seq[Client]], Duration.Inf)

      retrievedClients1.size shouldBe 2

      // Second page
      val offset2 = 2
      val limit2  = 3

      val response2 = request(
        uri = s"$serviceURL/clients?eServiceId=$eServiceId&offset=$offset2&limit=$limit2",
        method = HttpMethods.GET
      )

      response2.status shouldBe StatusCodes.OK
      val retrievedClients2 = Await.result(Unmarshal(response2).to[Seq[Client]], Duration.Inf)

      retrievedClients2.size shouldBe 1

      retrievedClients1.intersect(retrievedClients2) shouldBe empty
    }

    "not be retrieved without required fields" in {
      val response = request(uri = s"$serviceURL/clients", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.BadRequest
    }

  }

}
