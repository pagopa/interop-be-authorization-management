package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.Client
import it.pagopa.pdnd.interop.uservice.keymanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
class KeyManagementServiceSpec
    extends ScalaTestWithActorTestKit(SpecConfiguration.config)
    with AnyWordSpecLike
    with SpecConfiguration
    with SpecHelper {

  override def beforeAll(): Unit = {
    startServer()
  }

  override def afterAll(): Unit = {
    println("****** Cleaning resources ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    super.afterAll()
    println("Resources cleaned")
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

      // Add operators
      val operatorRequest1 = s"""{"operatorId": "${operatorId1.toString}"}"""
      val operatorRequest2 = s"""{"operatorId": "${operatorId2.toString}"}"""

      request(
        uri = s"$serviceURL/clients/${clientId1.toString}/operators",
        method = HttpMethods.POST,
        data = Some(operatorRequest1)
      )

      request(
        uri = s"$serviceURL/clients/${clientId2.toString}/operators",
        method = HttpMethods.POST,
        data = Some(operatorRequest2)
      )

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

  "Key creation should" should {
    "fail if client does not exist" in {
      val data =
        s"""
           |[
           |  {
           |    "operatorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "sig",
           |    "alg": "123"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/non-existing-client-id/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.BadRequest
    }

    "succeed if client exists" in {
      val clientId    = UUID.fromString("638f76d2-39b3-47f2-bd65-a1e6094d35e9")
      val agreementId = UUID.fromString("5d2ca309-8871-412f-8073-3ab1da0148b7")

      createClient(clientId, agreementId)

      val data =
        s"""
           |[
           |  {
           |    "operatorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "sig",
           |    "alg": "123"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.Created
    }
  }

}
