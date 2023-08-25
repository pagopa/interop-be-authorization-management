package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.authorizationmanagement.api.impl._
import it.pagopa.interop.authorizationmanagement.model.{Client, Keys}
import it.pagopa.interop.authorizationmanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
class RelationshipManagementSpec
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

  "Relationship creation" should {
    "be successful on existing client" in {
      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      createClient(clientId, consumerId)

      val data = s"""{
                    |  "relationshipId": "${relationshipId.toString}"
                    |}""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/$clientId/relationships", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.OK
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient.relationships shouldBe Set(relationshipId)
    }

    "fail if client does not exist" in {
      val clientId       = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      val data = s"""{
                    |  "relationshipId": "${relationshipId.toString}"
                    |}""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/$clientId/relationships", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.NotFound
    }
  }

  "Relationship deletion" should {
    "be successful" in {
      val clientId        = UUID.randomUUID()
      val consumerId      = UUID.randomUUID()
      val relationshipId1 = UUID.randomUUID()
      val relationshipId2 = UUID.randomUUID()

      createClient(clientId, consumerId)
      addRelationship(clientId, relationshipId1)
      addRelationship(clientId, relationshipId2)
      val keyOne = createKey(clientId, relationshipId1)
      val keyTwo = createKey(clientId, relationshipId2)

      val response =
        request(uri = s"$serviceURL/clients/$clientId/relationships/$relationshipId1", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      val clientRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId", method = HttpMethods.GET)
      val retrievedClient        = Await.result(Unmarshal(clientRetrieveResponse).to[Client], Duration.Inf)
      retrievedClient.relationships shouldBe Set(relationshipId2)

      val keysRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId/keys", method = HttpMethods.GET)
      val retrievedKeys        = Await.result(Unmarshal(keysRetrieveResponse).to[Keys], Duration.Inf)
      retrievedKeys.keys shouldEqual (keyOne.keys ++ keyTwo.keys)
    }

    "be successful and don't remove same relationship keys of different client" in {
      val clientId1      = UUID.randomUUID()
      val clientId2      = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)
      addRelationship(clientId1, relationshipId)
      addRelationship(clientId2, relationshipId)
      createKey(clientId1, relationshipId)
      val client2Keys = createKey(clientId2, relationshipId)

      val response =
        request(uri = s"$serviceURL/clients/$clientId1/relationships/$relationshipId", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      val clientRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId2", method = HttpMethods.GET)
      val retrievedClient        = Await.result(Unmarshal(clientRetrieveResponse).to[Client], Duration.Inf)
      retrievedClient.relationships shouldBe Set(relationshipId)

      val keysRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId2/keys", method = HttpMethods.GET)
      val retrievedKeys        = Await.result(Unmarshal(keysRetrieveResponse).to[Keys], Duration.Inf)
      retrievedKeys shouldBe client2Keys
    }

  }

}
