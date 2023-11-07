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

  "User creation" should {
    "be successful on existing client" in {
      val clientId   = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val userId     = UUID.randomUUID()

      createClient(clientId, consumerId)

      val data = s"""{
                    |  "userId": "${userId.toString}"
                    |}""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/$clientId/users", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.OK
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient.users shouldBe Set(userId)
    }

    "fail if client does not exist" in {
      val clientId = UUID.randomUUID()
      val userId   = UUID.randomUUID()

      val data = s"""{
                    |  "userId": "${userId.toString}"
                    |}""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/$clientId/users", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.NotFound
    }
  }

  "User deletion" should {
    "be successful" in {
      val clientId   = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val userId1    = UUID.randomUUID()
      val userId2    = UUID.randomUUID()

      createClient(clientId, consumerId)
      addUser(clientId, userId1)
      addUser(clientId, userId2)
      val keyOne = createKey(clientId, userId1)
      val keyTwo = createKey(clientId, userId2)

      val clientRetrieveResponse1 = request(uri = s"$serviceURL/clients/$clientId", method = HttpMethods.GET)
      val retrievedClient1        = Await.result(Unmarshal(clientRetrieveResponse1).to[Client], Duration.Inf)
      retrievedClient1.users shouldBe Set(userId1, userId2)

      val response =
        request(uri = s"$serviceURL/clients/$clientId/users/$userId1", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      val clientRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId", method = HttpMethods.GET)
      val retrievedClient        = Await.result(Unmarshal(clientRetrieveResponse).to[Client], Duration.Inf)
      retrievedClient.users shouldBe Set(userId2)

      val keysRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId/keys", method = HttpMethods.GET)
      val retrievedKeys        = Await.result(Unmarshal(keysRetrieveResponse).to[Keys], Duration.Inf)
      retrievedKeys.keys shouldEqual (keyOne.keys ++ keyTwo.keys)
    }

    "be successful and don't remove same user keys of different client" in {
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val userId     = UUID.randomUUID()

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)
      addUser(clientId1, userId)
      addUser(clientId2, userId)
      createKey(clientId1, userId)
      val client2Keys = createKey(clientId2, userId)

      val response =
        request(uri = s"$serviceURL/clients/$clientId1/users/$userId", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      val clientRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId2", method = HttpMethods.GET)
      val retrievedClient        = Await.result(Unmarshal(clientRetrieveResponse).to[Client], Duration.Inf)
      retrievedClient.users shouldBe Set(userId)

      val keysRetrieveResponse = request(uri = s"$serviceURL/clients/$clientId2/keys", method = HttpMethods.GET)
      val retrievedKeys        = Await.result(Unmarshal(keysRetrieveResponse).to[Keys], Duration.Inf)
      retrievedKeys shouldBe client2Keys
    }

  }

}
