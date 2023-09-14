package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.authorizationmanagement.api.impl._
import it.pagopa.interop.authorizationmanagement.model.ClientComponentState.{ACTIVE, INACTIVE}
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.enrichAny

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
      (() => mockUUIDSupplier.get()).expects().returning(newClientUuid).once()

      val consumerUuid = UUID.randomUUID()
      val name         = "New Client 1"
      val description  = Some("New Client 1 description")

      val expected =
        Client(
          id = newClientUuid,
          consumerId = consumerUuid,
          name = name,
          purposes = Seq.empty,
          description = description,
          relationships = Set.empty,
          kind = ClientKind.CONSUMER,
          createdAt = timestamp
        )

      val data =
        s"""{
           |  "consumerId": "${consumerUuid.toString}",
           |  "name": "$name",
           |  "kind": "CONSUMER",
           |  "description": "${description.get}",
           |  "createdAt": "$timestamp",
           |  "members": []
           |}""".stripMargin

      val response = request(uri = s"$serviceURL/clients", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.OK
      val createdClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      createdClient shouldBe expected

    }

    "be retrieved successfully" in {
      val clientUuid    = UUID.randomUUID()
      val consumerUuid  = UUID.randomUUID()
      val createdClient = createClient(clientUuid, consumerUuid)

      val response = request(uri = s"$serviceURL/clients/$clientUuid", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient shouldBe createdClient
    }

    "be retrieved by asking for a purposeId it is associated with" in {
      val clientId          = UUID.randomUUID()
      val consumerId        = UUID.randomUUID()
      val purposeId1        = UUID.randomUUID()
      val purposeVersionId1 = UUID.randomUUID()
      val eServiceId        = UUID.randomUUID()
      val descriptorId      = UUID.randomUUID()
      val agreementId       = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()

      createClient(clientId, consumerId)

      (() => mockUUIDSupplier.get()).expects().returning(statesChainId).once()

      val payload1 = PurposeSeed(states =
        ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            descriptorId = descriptorId,
            state = ACTIVE,
            audience = Seq("some.audience"),
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = eServiceId,
            consumerId = consumerId,
            agreementId = agreementId,
            state = INACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = purposeId1,
            versionId = purposeVersionId1,
            state = ClientComponentState.ACTIVE
          )
        )
      )

      val _ =
        request(
          uri = s"$serviceURL/clients/$clientId/purposes",
          method = HttpMethods.POST,
          data = Some(payload1.toJson.prettyPrint)
        )

      val response =
        request(uri = s"$serviceURL/clients/$clientId/purposes/$purposeId1", method = HttpMethods.GET, data = None)

      val expected =
        Client(
          id = clientId,
          consumerId = consumerId,
          name = s"New Client ${clientId.toString}",
          description = Some(s"New Client ${clientId.toString} description"),
          kind = ClientKind.CONSUMER,
          purposes = Seq(
            Purpose(states =
              ClientStatesChain(
                id = statesChainId,
                eservice = ClientEServiceDetails(
                  eserviceId = eServiceId,
                  descriptorId = descriptorId,
                  state = ClientComponentState.ACTIVE,
                  audience = Seq("some.audience"),
                  voucherLifespan = 10
                ),
                agreement = ClientAgreementDetails(
                  eserviceId = eServiceId,
                  consumerId = consumerId,
                  agreementId = agreementId,
                  state = ClientComponentState.INACTIVE
                ),
                purpose = ClientPurposeDetails(
                  purposeId = purposeId1,
                  versionId = purposeVersionId1,
                  state = ClientComponentState.ACTIVE
                )
              )
            )
          ),
          relationships = Set.empty,
          createdAt = timestamp
        )

      val client = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      response.status shouldBe StatusCodes.OK
      client shouldBe expected
    }

    "fail if it is not associated with a purposeId" in {
      val clientId         = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()
      val purposeId        = UUID.randomUUID()
      val purposeVersionId = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val descriptorId     = UUID.randomUUID()
      val agreementId      = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()

      createClient(clientId, consumerId)

      (() => mockUUIDSupplier.get()).expects().returning(statesChainId).once()

      val payload = PurposeSeed(states =
        ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            descriptorId = descriptorId,
            state = ClientComponentState.ACTIVE,
            audience = Seq("some.audience"),
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = eServiceId,
            consumerId = consumerId,
            agreementId = agreementId,
            state = ClientComponentState.INACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = purposeId,
            versionId = purposeVersionId,
            state = ClientComponentState.ACTIVE
          )
        )
      )

      val _ =
        request(
          uri = s"$serviceURL/clients/$clientId/purposes",
          method = HttpMethods.POST,
          data = Some(payload.toJson.prettyPrint)
        )

      val response =
        request(
          uri = s"$serviceURL/clients/$clientId/purposes/${UUID.randomUUID()}",
          method = HttpMethods.GET,
          data = None
        )

      response.status shouldBe StatusCodes.NotFound
    }

  }

  "Client deletion" should {

    "succeed" in {
      val clientUuid       = UUID.randomUUID()
      val consumerUuid     = UUID.randomUUID()
      val relationshipUuid = UUID.randomUUID()

      createClient(clientUuid, consumerUuid)
      addRelationship(clientUuid, relationshipUuid)
      createKey(clientUuid, relationshipUuid)

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
    "correctly filter by relationship id" in {
      val clientId1       = UUID.randomUUID()
      val clientId2       = UUID.randomUUID()
      val consumerId1     = UUID.randomUUID()
      val consumerId2     = UUID.randomUUID()
      val relationshipId1 = UUID.randomUUID()
      val relationshipId2 = UUID.randomUUID()

      createClient(clientId1, consumerId1)
      createClient(clientId2, consumerId2)

      addRelationship(clientId1, relationshipId1)
      addRelationship(clientId2, relationshipId2)

      // List clients
      val response = request(uri = s"$serviceURL/clients?relationshipId=$relationshipId1", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size shouldBe 1
      retrievedClients.head.id shouldBe clientId1

    }

    "correctly filter by consumer id" in {
      val clientId1   = UUID.randomUUID()
      val clientId2   = UUID.randomUUID()
      val clientId3   = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()
      val consumerId3 = UUID.randomUUID()

      val client1 = createClient(clientId1, consumerId1)
      val client2 = createClient(clientId2, consumerId1)
      createClient(clientId3, consumerId3)

      val response = request(uri = s"$serviceURL/clients?consumerId=$consumerId1", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size shouldBe 2
      retrievedClients should contain only (client1, client2)

    }

    "correctly filter by purpose id" in {
      val clientId1         = UUID.randomUUID()
      val clientId2         = UUID.randomUUID()
      val clientId3         = UUID.randomUUID()
      val consumerId        = UUID.randomUUID()
      val purposeId1        = UUID.randomUUID()
      val purposeId2        = UUID.randomUUID()
      val purposeVersionId1 = UUID.randomUUID()

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)
      createClient(clientId3, consumerId)

      val eServiceDetailsSeed = ClientEServiceDetailsSeed(
        eserviceId = UUID.randomUUID(),
        descriptorId = UUID.randomUUID(),
        state = ACTIVE,
        audience = Seq("some.audience"),
        voucherLifespan = 10
      )

      val agreementDetailsSeed = ClientAgreementDetailsSeed(
        eserviceId = UUID.randomUUID(),
        consumerId = UUID.randomUUID(),
        agreementId = UUID.randomUUID(),
        state = INACTIVE
      )

      val purposeDetailsSeed1 = ClientPurposeDetailsSeed(
        purposeId = purposeId1,
        versionId = purposeVersionId1,
        state = ClientComponentState.ACTIVE
      )

      val purposeSeed1 = PurposeSeed(states =
        ClientStatesChainSeed(
          eservice = eServiceDetailsSeed,
          agreement = agreementDetailsSeed,
          purpose = purposeDetailsSeed1.copy(purposeId = purposeId1)
        )
      )

      val purposeSeed2 =
        PurposeSeed(states =
          ClientStatesChainSeed(
            eservice = eServiceDetailsSeed,
            agreement = agreementDetailsSeed,
            purpose = purposeDetailsSeed1.copy(purposeId = purposeId2)
          )
        )

      addPurposeState(clientId1, purposeSeed1, UUID.randomUUID())
      addPurposeState(clientId2, purposeSeed1, UUID.randomUUID())
      addPurposeState(clientId3, purposeSeed2, UUID.randomUUID())

      val response =
        request(uri = s"$serviceURL/clients?purposeId=$purposeId1", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size shouldBe 2
      retrievedClients.map(_.id) should contain theSameElementsAs Seq(clientId1, clientId2)

    }

    "correctly retrieved without filters" in {
      val clientId1   = UUID.randomUUID()
      val consumerId1 = UUID.randomUUID()

      createClient(clientId1, consumerId1)

      val response = request(uri = s"$serviceURL/clients", method = HttpMethods.GET)

      response.status shouldBe StatusCodes.OK
      val retrievedClients = Await.result(Unmarshal(response).to[Seq[Client]], Duration.Inf)

      retrievedClients.size should be > 0
    }

    "correctly paginate elements" in {
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val clientId3  = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)
      createClient(clientId3, consumerId)

      // First page
      val offset1 = 0
      val limit1  = 2

      val response1 = request(
        uri = s"$serviceURL/clients?consumerId=$consumerId&offset=$offset1&limit=$limit1",
        method = HttpMethods.GET
      )

      response1.status shouldBe StatusCodes.OK
      val retrievedClients1 = Await.result(Unmarshal(response1).to[Seq[Client]], Duration.Inf)

      retrievedClients1.size shouldBe 2

      // Second page
      val offset2 = 2
      val limit2  = 3

      val response2 = request(
        uri = s"$serviceURL/clients?consumerId=$consumerId&offset=$offset2&limit=$limit2",
        method = HttpMethods.GET
      )

      response2.status shouldBe StatusCodes.OK
      val retrievedClients2 = Await.result(Unmarshal(response2).to[Seq[Client]], Duration.Inf)

      retrievedClients2.size shouldBe 1

      retrievedClients1.intersect(retrievedClients2) shouldBe empty
    }

  }

}
