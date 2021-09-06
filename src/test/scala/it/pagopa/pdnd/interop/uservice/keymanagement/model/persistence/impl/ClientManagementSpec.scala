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
      val newClientUuid = UUID.fromString("8113bd7c-8c31-4912-ac05-883c25347960")
      (() => mockUUIDSupplier.get).expects().returning(newClientUuid).once()

      val agreementUuid = UUID.fromString("24772a3d-e6f2-47f2-96e5-4cbd1e4e8c85")
      val description   = "New Client 1"

      val expected =
        Client(id = newClientUuid, agreementId = agreementUuid, description = description, operators = Set.empty)

      val data =
        s"""{
           |  "agreementId": "${agreementUuid.toString}",
           |  "description": "$description"
           |}""".stripMargin

      val response = request(uri = s"$serviceURL/clients", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.Created
      val createdClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      createdClient shouldBe expected

    }

    "be retrieved successfully" in {
      val clientUuid    = UUID.fromString("f3447128-4695-418f-9658-959da0ee3f8c")
      val agreementUuid = UUID.fromString("48e948cc-a60a-4d93-97e9-bcbe1d6e5283")
      val createdClient = createClient(clientUuid, agreementUuid)

      val response = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient shouldBe createdClient
    }

    "be deleted successfully" in {
      val clientUuid    = UUID.fromString("d8803fae-daf9-4bf4-94b0-c495005b1a4b")
      val agreementUuid = UUID.fromString("d75544b4-bc16-45d4-8dbf-b0842e9f9dca")
      val operatorUuid  = UUID.fromString("ae7b1133-446a-462c-ab79-2016c1168dde")

      createClient(clientUuid, agreementUuid)
      addOperator(clientUuid, operatorUuid)
      createKey(clientUuid, operatorUuid)

      val deleteResponse = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.DELETE)
      deleteResponse.status shouldBe StatusCodes.NoContent

      val retrieveClientResponse = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.GET)
      retrieveClientResponse.status shouldBe StatusCodes.NotFound

      val retrieveKeysResponse = request(uri = s"$serviceURL/$clientUuid/keys", method = HttpMethods.GET)
      retrieveKeysResponse.status shouldBe StatusCodes.NotFound
    }

    "fail on non-existing client id" in {
      val deleteResponse = request(uri = s"$serviceURL/clients/non-existing-id", method = HttpMethods.DELETE)
      deleteResponse.status shouldBe StatusCodes.NotFound
    }

  }

  "Client list" should {
    "correctly filter by agreement id" in {
      val clientId1    = UUID.fromString("8198d261-2e2f-4ef4-a7a2-1d7ea4cef472")
      val clientId2    = UUID.fromString("dea175f3-3180-42cf-9c1b-048a67c6e4ff")
      val clientId3    = UUID.fromString("d78fe4d4-9518-4ea1-bf44-e1a86db080bd")
      val agreementId1 = UUID.fromString("4922fcfe-206f-41a1-83ea-f2647b4eeb3c")
      val agreementId3 = UUID.fromString("58277d2a-8b68-4609-89d1-bae23100e964")

      val client1 = createClient(clientId1, agreementId1)
      val client2 = createClient(clientId2, agreementId1)
      createClient(clientId3, agreementId3)

      val response = request(uri = s"$serviceURL/clients?agreementId=$agreementId1", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size shouldBe 2
      retrievedClients should contain only (client1, client2)

    }

    "correctly filter by operator id" in {
      val clientId1    = UUID.fromString("264e0348-c465-4739-9970-3b79953a91aa")
      val clientId2    = UUID.fromString("ef118f0d-c5a9-4b8e-8a1d-9c844efdfc3b")
      val agreementId1 = UUID.fromString("39e20f27-9052-4c55-9e79-a7dc2f706a6e")
      val agreementId2 = UUID.fromString("53ed4323-a62c-485f-956a-fc2b1ce4b90b")
      val operatorId1  = UUID.fromString("13c91530-c015-4fa0-8631-933277d47394")
      val operatorId2  = UUID.fromString("4fbce7d2-e382-44d1-a7cb-6436891a568e")

      createClient(clientId1, agreementId1)
      createClient(clientId2, agreementId2)

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
      val clientId1    = UUID.fromString("a24ee7f9-f445-4e77-a77f-c21dfbce4bed")
      val clientId2    = UUID.fromString("d0b551d7-57c2-4373-afa4-ba3a3413caad")
      val clientId3    = UUID.fromString("ed228675-b9db-46fc-92e7-bd6cb3f7a306")
      val agreementId1 = UUID.fromString("9fa239f7-6189-4165-a95d-64c87324b122")

      createClient(clientId1, agreementId1)
      createClient(clientId2, agreementId1)
      createClient(clientId3, agreementId1)

      // First page
      val offset1 = 0
      val limit1  = 2

      val response1 = request(
        uri = s"$serviceURL/clients?agreementId=$agreementId1&offset=$offset1&limit=$limit1",
        method = HttpMethods.GET
      )

      response1.status shouldBe StatusCodes.OK
      val retrievedClients1 = Await.result(Unmarshal(response1).to[Seq[Client]], Duration.Inf)

      retrievedClients1.size shouldBe 2

      // Second page
      val offset2 = 2
      val limit2  = 3

      val response2 = request(
        uri = s"$serviceURL/clients?agreementId=$agreementId1&offset=$offset2&limit=$limit2",
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
