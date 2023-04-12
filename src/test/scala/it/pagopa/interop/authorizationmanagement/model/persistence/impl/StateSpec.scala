package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import it.pagopa.interop.authorizationmanagement.model.client.{Consumer, PersistentClient}
import it.pagopa.interop.authorizationmanagement.model.key.{PersistentKey, Sig}
import it.pagopa.interop.authorizationmanagement.model.persistence.{ClientDeleted, KeyDeleted, State}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime
import java.util.UUID

class StateSpec extends AnyWordSpecLike with Matchers {

  "given an application state" should {

    "physically delete the keys properly" in {
      // given
      val relationshipId = UUID.randomUUID()
      val fooBarKeys     = Map(
        "1" -> PersistentKey(
          kid = "1",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        ),
        "2" -> PersistentKey(
          kid = "2",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        ),
        "3" -> PersistentKey(
          kid = "3",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        ),
        "4" -> PersistentKey(
          kid = "4",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        )
      )
      val keys           = Map("fooBarKeys" -> fooBarKeys)
      val state          = State(keys = keys, clients = Map.empty)
      state.keys("fooBarKeys").size shouldBe 4

      // when
      val updatedState = state.deleteKey(KeyDeleted("fooBarKeys", "2", OffsetDateTime.now()))

      // then
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

      val consumerUuid   = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      // given
      val client1Keys = Map(
        "kid1" -> PersistentKey(
          kid = "kid1",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        )
      )
      val client2Keys = Map(
        "kid2" -> PersistentKey(
          kid = "kid2",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        )
      )
      val client3Keys = Map(
        "kid3" -> PersistentKey(
          kid = "kid3",
          relationshipId = relationshipId,
          name = "Random Key",
          encodedPem = "123",
          use = Sig,
          algorithm = "sha",
          createdAt = OffsetDateTime.now()
        )
      )
      val client1     =
        PersistentClient(
          id = clientUuid1,
          consumerId = consumerUuid,
          name = "client 1",
          purposes = Seq.empty,
          description = Some("client 1 desc"),
          relationships = Set.empty,
          kind = Consumer,
          createdAt = OffsetDateTime.now()
        )
      val client2     =
        PersistentClient(
          id = clientUuid2,
          consumerId = consumerUuid,
          name = "client 2",
          purposes = Seq.empty,
          description = Some("client 2 desc"),
          relationships = Set.empty,
          kind = Consumer,
          createdAt = OffsetDateTime.now()
        )
      val client3     =
        PersistentClient(
          id = clientUuid3,
          consumerId = consumerUuid,
          name = "client 3",
          purposes = Seq.empty,
          description = Some("client 3 desc"),
          relationships = Set.empty,
          kind = Consumer,
          createdAt = OffsetDateTime.now()
        )

      val keys    = Map(clientId1 -> client1Keys, clientId2 -> client2Keys, clientId3 -> client3Keys)
      val clients = Map(clientId1 -> client1, clientId2 -> client2, clientId3 -> client3)
      val state   = State(keys = keys, clients = clients)

      // when
      val updatedState = state.deleteClient(ClientDeleted(clientId2))

      // then
      updatedState.keys.get(clientId2) shouldBe None
      updatedState.keys.size shouldBe 2
      updatedState.clients.get(clientId2) shouldBe None
      updatedState.clients.size shouldBe 2
    }
  }

}
