package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.State
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClient
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.key.{Active, Deleted, Disabled, PersistentKey}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime
import java.util.UUID

class StateSpec extends AnyWordSpecLike with Matchers {

  "given an application state" should {

    "physically delete the keys properly" in {
      //given
      val fooBarKeys = Map(
        "1" -> PersistentKey(
          kid = "1",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "2" -> PersistentKey(
          kid = "2",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "3" -> PersistentKey(
          kid = "3",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "4" -> PersistentKey(
          kid = "4",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Disabled
        )
      )
      val keys  = Map("fooBarKeys" -> fooBarKeys)
      val state = State(keys = keys, clients = Map.empty)
      state.keys.get("fooBarKeys").get.size shouldBe 4

      //when
      val updatedState = state.deleteKey("fooBarKeys", "2")

      //then
      updatedState.keys.get("fooBarKeys").flatMap(_.get("2")) shouldBe None
      updatedState.keys.get("fooBarKeys").get.size shouldBe 3
      updatedState.getActiveClientKeyById("fooBarKeys", "1").get.status shouldBe Active
    }

    "disable the keys properly" in {
      //given
      val fooBarKeys = Map(
        "1" -> PersistentKey(
          kid = "1",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "2" -> PersistentKey(
          kid = "2",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        )
      )
      val keys  = Map("fooBarKeys" -> fooBarKeys)
      val state = State(keys = keys, clients = Map.empty)
      val time  = OffsetDateTime.now()

      //when
      val updatedState = state.disable("fooBarKeys", "2", time)

      //then
      updatedState.keys.get("fooBarKeys").flatMap(_.get("2")).get.status shouldBe Disabled
      updatedState.keys.get("fooBarKeys").flatMap(_.get("2")).get.deactivationTimestamp shouldBe Some(time)
      updatedState.getActiveClientKeyById(
        "fooBarKeys",
        "2"
      ) shouldBe None //since the API method returns active keys only

      updatedState.getActiveClientKeyById("fooBarKeys", "1").get.status shouldBe Active
      updatedState.getActiveClientKeyById("fooBarKeys", "1").get.deactivationTimestamp shouldBe None
    }

    "disabling the key and then reactivating it should work properly" in {
      //given
      val fooBarKeys = Map(
        "1" -> PersistentKey(
          kid = "1",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "2" -> PersistentKey(
          kid = "2",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        )
      )
      val keys  = Map("fooBarKeys" -> fooBarKeys)
      val state = State(keys = keys, clients = Map.empty)
      val time  = OffsetDateTime.now()

      //when
      val updatedState = state.disable("fooBarKeys", "2", time)
      //then
      updatedState.keys.get("fooBarKeys").flatMap(_.get("2")).get.status shouldBe Disabled
      updatedState.keys.get("fooBarKeys").flatMap(_.get("2")).get.deactivationTimestamp shouldBe Some(time)

      //when
      val updatedUpdatedState = updatedState.enable("fooBarKeys", "2")

      //then
      updatedUpdatedState.getActiveClientKeyById("fooBarKeys", "2").get.status shouldBe Active
      updatedUpdatedState.getActiveClientKeyById("fooBarKeys", "2").get.deactivationTimestamp shouldBe None
    }

    "return only the list of active keys" in {
      //given
      val fooBarKeys = Map(
        "1" -> PersistentKey(
          kid = "1",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "2" -> PersistentKey(
          kid = "2",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Deleted
        ),
        "3" -> PersistentKey(
          kid = "3",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9222"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        ),
        "4" -> PersistentKey(
          kid = "4",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Disabled
        )
      )
      val keys  = Map("fooBarKeys" -> fooBarKeys)
      val state = State(keys = keys, clients = Map.empty)

      //when
      val activeKeys = state.getClientActiveKeys("fooBarKeys")

      //then
      activeKeys shouldBe a[Some[_]]
      activeKeys.get.keys should contain allOf ("1", "3")
      activeKeys.get.values.map(
        _.operatorId.toString
      ) should contain allOf ("27f8dce0-0a5b-476b-9fdd-a7a658eb9215", "27f8dce0-0a5b-476b-9fdd-a7a658eb9222")
      activeKeys.get.keys shouldNot contain allOf ("2", "4")
    }

    "delete a client properly" in {
      val clientId1 = "ab1fd21e-8683-4ca8-abd7-8101eed605d1"
      val clientId2 = "35c77b88-6a39-4bff-ae2d-31b39bc3502f"
      val clientId3 = "b81a7732-a33d-4c2e-b9f3-cf190238c5dd"

      val clientUuid1 = UUID.fromString(clientId1)
      val clientUuid2 = UUID.fromString(clientId2)
      val clientUuid3 = UUID.fromString(clientId3)

      val eServiceUuid = UUID.fromString("f4d3d3e2-e71c-4e80-8499-b555dd18bb5c")

      //given
      val client1Keys = Map(
        "kid1" -> PersistentKey(
          kid = "kid1",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        )
      )
      val client2Keys = Map(
        "kid2" -> PersistentKey(
          kid = "kid2",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        )
      )
      val client3Keys = Map(
        "kid3" -> PersistentKey(
          kid = "kid3",
          operatorId = UUID.fromString("27f8dce0-0a5b-476b-9fdd-a7a658eb9215"),
          encodedPem = "123",
          use = "sig",
          algorithm = "sha",
          creationTimestamp = OffsetDateTime.now(),
          deactivationTimestamp = None,
          status = Active
        )
      )
      val client1 = PersistentClient(clientUuid1, eServiceUuid, "client 1", Some("client 1 desc"), Set.empty)
      val client2 = PersistentClient(clientUuid2, eServiceUuid, "client 2", Some("client 2 desc"), Set.empty)
      val client3 = PersistentClient(clientUuid3, eServiceUuid, "client 3", Some("client 3 desc"), Set.empty)

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

  }

}
