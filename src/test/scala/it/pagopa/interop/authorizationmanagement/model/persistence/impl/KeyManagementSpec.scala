package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.authorizationmanagement.api.impl.KeyFormat
import it.pagopa.interop.authorizationmanagement.model.Key
import it.pagopa.interop.authorizationmanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/** Local integration test.
  *
  * Starts a local cluster sharding and invokes REST operations on the event sourcing entity
  */
class KeyManagementSpec
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

  "Key creation" should {
    "fail if client does not exist" in {

      val data =
        s"""
           |[
           |  {
           |    "relationshipId": "2ce51cdd-ae78-4829-89cc-39e363270b53",
           |    "kid": "kid",
           |    "encodedPem": "${generateEncodedKey()}",
           |    "name": "Test",
           |    "use": "SIG",
           |    "algorithm": "123",
           |    "createdAt": "$timestamp"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/non-existing-client-id/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if key relationship has not been added to client" in {
      val clientId   = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      createClient(clientId, consumerId)

      val data =
        s"""
           |[
           |  {
           |    "relationshipId": "2ce51cdd-ae78-4829-89cc-39e363270b53",
           |    "kid": "kid",
           |    "encodedPem": "${generateEncodedKey()}",
           |    "name": "Test",
           |    "use": "SIG",
           |    "algorithm": "123",
           |    "createdAt": "$timestamp"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.BadRequest
    }

    "succeed" in {
      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      createClient(clientId, consumerId, Seq(relationshipId))
      addRelationship(clientId, relationshipId)

      val data =
        s"""
           |[
           |  {
           |    "relationshipId": "$relationshipId",
           |    "kid": "kid",
           |    "encodedPem": "${generateEncodedKey()}",
           |    "name": "Test",
           |    "use": "SIG",
           |    "algorithm": "123",
           |    "createdAt": "$timestamp"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.OK
    }
  }

  "Key retrieve" should {
    "return existing keys" in {
      val clientUuid       = UUID.randomUUID()
      val consumerUuid     = UUID.randomUUID()
      val relationshipUuid = UUID.randomUUID()

      createClient(clientUuid, consumerUuid, Seq(relationshipUuid))
      addRelationship(clientUuid, relationshipUuid)
      createKey(clientUuid, relationshipUuid)

      val response = request(uri = s"$serviceURL/clients/$clientUuid/keys", method = HttpMethods.GET)
      response.status shouldBe StatusCodes.OK

      val retrievedKeys = Await.result(Unmarshal(response).to[Seq[Key]], Duration.Inf)
      retrievedKeys.length shouldBe 1
    }

    "return empty list if client has no keys" in {
      val clientUuid   = UUID.randomUUID()
      val consumerUuid = UUID.randomUUID()

      createClient(clientUuid, consumerUuid)

      val response = request(uri = s"$serviceURL/clients/$clientUuid/keys", method = HttpMethods.GET)
      response.status shouldBe StatusCodes.OK

      val retrievedKeys = Await.result(Unmarshal(response).to[Seq[Key]], Duration.Inf)
      retrievedKeys shouldBe Seq.empty
    }
  }
}
