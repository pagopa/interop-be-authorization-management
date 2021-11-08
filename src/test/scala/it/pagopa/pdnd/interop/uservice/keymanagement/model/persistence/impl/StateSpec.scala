package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{State, client}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.PersistentKey
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime
import java.util.UUID

class StateSpec extends AnyWordSpecLike with Matchers {

  "given an application state" should {

    "physically delete the keys properly" in {
      //given
      val relationshipId = UUID.randomUUID()
      val fooBarKeys = Map(
        "1" -> PersistentKey(
          kid = "1",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        ),
        "2" -> PersistentKey(
          kid = "2",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        ),
        "3" -> PersistentKey(
          kid = "3",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        ),
        "4" -> PersistentKey(
          kid = "4",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val keys  = Map("fooBarKeys" -> fooBarKeys)
      val state = State(keys = keys, clients = Map.empty)
      state.keys("fooBarKeys").size shouldBe 4

      //when
      val updatedState = state.deleteKey("fooBarKeys", "2")

      //then
      updatedState.keys.get("fooBarKeys").flatMap(_.get("2")) shouldBe None
      updatedState.keys("fooBarKeys").size shouldBe 3
    }

    "delete a client properly" in {
      val clientUuid1 = UUID.randomUUID()
      val clientUuid2 = UUID.randomUUID()
      val clientUuid3 = UUID.randomUUID()

      val clientId1 = clientUuid1.toString
      val clientId2 = clientUuid2.toString
      val clientId3 = clientUuid3.toString

      val eServiceUuid   = UUID.randomUUID()
      val consumerUuid   = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      //given
      val client1Keys = Map(
        "kid1" -> PersistentKey(
          kid = "kid1",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client2Keys = Map(
        "kid2" -> PersistentKey(
          kid = "kid2",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client3Keys = Map(
        "kid3" -> PersistentKey(
          kid = "kid3",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client1 =
        PersistentClient(
          id = clientUuid1,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 1",
          status = client.Active,
          purposes = "purposes",
          description = Some("client 1 desc"),
          relationships = Set.empty
        )
      val client2 =
        PersistentClient(
          id = clientUuid2,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 2",
          status = client.Active,
          purposes = "purposes",
          description = Some("client 2 desc"),
          relationships = Set.empty
        )
      val client3 =
        PersistentClient(
          id = clientUuid3,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 3",
          status = client.Active,
          purposes = "purposes",
          description = Some("client 3 desc"),
          relationships = Set.empty
        )

      val keys    = Map(clientId1 -> client1Keys, clientId2 -> client2Keys, clientId3 -> client3Keys)
      val clients = Map(clientId1 -> client1, clientId2 -> client2, clientId3 -> client3)
      val state   = State(keys = keys, clients = clients)

      //when
      val updatedState = state.deleteClient(clientId2)

      //then
      updatedState.keys.get(clientId2) shouldBe None
      updatedState.keys.size shouldBe 2
      updatedState.clients.get(clientId2) shouldBe None
      updatedState.clients.size shouldBe 2
    }

    "activate a client" in {
      val clientUuid1 = UUID.randomUUID()
      val clientUuid2 = UUID.randomUUID()

      val clientId1 = clientUuid1.toString
      val clientId2 = clientUuid2.toString

      val eServiceUuid   = UUID.randomUUID()
      val consumerUuid   = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      //given
      val client1Keys = Map(
        "kid1" -> PersistentKey(
          kid = "kid1",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client2Keys = Map(
        "kid2" -> PersistentKey(
          kid = "kid2",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client1 =
        PersistentClient(
          id = clientUuid1,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 1",
          status = client.Active,
          purposes = "purposes",
          description = Some("client 1 desc"),
          relationships = Set.empty
        )
      val client2 =
        PersistentClient(
          id = clientUuid2,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 2",
          status = client.Suspended,
          purposes = "purposes",
          description = Some("client 2 desc"),
          relationships = Set.empty
        )

      val keys    = Map(clientId1 -> client1Keys, clientId2 -> client2Keys)
      val clients = Map(clientId1 -> client1, clientId2 -> client2)
      val state   = State(keys = keys, clients = clients)

      //when
      val updatedState = state.activateClient(clientId2)

      //then
      updatedState.keys shouldBe keys
      updatedState.clients.get(clientId1) should contain(client1)
      updatedState.clients.get(clientId2) should contain(client2.copy(status = client.Active))
      updatedState.clients.size shouldBe 2
    }

    "suspend a client" in {
      val clientUuid1 = UUID.randomUUID()
      val clientUuid2 = UUID.randomUUID()

      val clientId1 = clientUuid1.toString
      val clientId2 = clientUuid2.toString

      val eServiceUuid   = UUID.randomUUID()
      val consumerUuid   = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      //given
      val client1Keys = Map(
        "kid1" -> PersistentKey(
          kid = "kid1",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client2Keys = Map(
        "kid2" -> PersistentKey(
          kid = "kid2",
          relationshipId = relationshipId,
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now()
        )
      )
      val client1 =
        PersistentClient(
          id = clientUuid1,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 1",
          status = client.Active,
          purposes = "purposes",
          description = Some("client 1 desc"),
          relationships = Set.empty
        )
      val client2 =
        PersistentClient(
          id = clientUuid2,
          eServiceId = eServiceUuid,
          consumerId = consumerUuid,
          name = "client 2",
          status = client.Active,
          purposes = "purposes",
          description = Some("client 2 desc"),
          relationships = Set.empty
        )

      val keys    = Map(clientId1 -> client1Keys, clientId2 -> client2Keys)
      val clients = Map(clientId1 -> client1, clientId2 -> client2)
      val state   = State(keys = keys, clients = clients)

      //when
      val updatedState = state.suspendClient(clientId2)

      //then
      updatedState.keys shouldBe keys
      updatedState.clients.get(clientId1) should contain(client1)
      updatedState.clients.get(clientId2) should contain(client2.copy(status = client.Suspended))
      updatedState.clients.size shouldBe 2
    }
  }

}
