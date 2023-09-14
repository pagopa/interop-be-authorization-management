package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.authorizationmanagement.api.impl.keysFormat
import it.pagopa.interop.authorizationmanagement.model.Keys
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
           |    "relationshipId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "SIG",
           |    "name": "Devedeset devet",
           |    "alg": "123"
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
           |    "relationshipId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "SIG",
           |    "name": "Devedeset devet",
           |    "alg": "123"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if key is not a valid PEM" in {

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
           |    "key": "LS0tLS1CRUdJ",
           |    "use": "SIG",
           |    "name": "Devet",
           |    "alg": "123",
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
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "SIG",
           |    "name": "Devet",
           |    "alg": "123",
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

      val retrievedKeys = Await.result(Unmarshal(response).to[Keys], Duration.Inf)
      retrievedKeys.keys.length shouldBe 1
    }

    "return empty list if client has no keys" in {
      val clientUuid   = UUID.randomUUID()
      val consumerUuid = UUID.randomUUID()

      createClient(clientUuid, consumerUuid)

      val response = request(uri = s"$serviceURL/clients/$clientUuid/keys", method = HttpMethods.GET)
      response.status shouldBe StatusCodes.OK

      val retrievedKeys = Await.result(Unmarshal(response).to[Keys], Duration.Inf)
      retrievedKeys.keys shouldBe Seq.empty
    }
  }
}
