package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.http.scaladsl.Http
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
        Client(id = newClientUuid, agreementId = agreementUuid, description = description, operators = Seq.empty)

      val data =
        s"""{
           |  "agreementId": "${agreementUuid.toString}",
           |  "description": "$description"
           |}""".stripMargin

      val response = Await.result(
        Http()(classicSystem).singleRequest(
          HttpRequest(
            uri = s"$serviceURL/clients",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, data)
          )
        ),
        Duration.Inf
      )

      response.status shouldBe StatusCodes.Created
      val createdClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      createdClient shouldBe expected

    }

    "be retrieved successfully" in {
      val clientUuid = UUID.fromString("f3447128-4695-418f-9658-959da0ee3f8c")
      val agreementUuid = UUID.fromString("48e948cc-a60a-4d93-97e9-bcbe1d6e5283")
      val createdClient = createClient(clientUuid, agreementUuid)

      val response = Await.result(
        Http()(classicSystem).singleRequest(
          HttpRequest(
            uri = s"$serviceURL/clients/$clientUuid",
            method = HttpMethods.GET
          )
        ),
        Duration.Inf
      )

      response.status shouldBe StatusCodes.OK
      val retrievedClient = Await.result(Unmarshal(response).to[Client], Duration.Inf)

      retrievedClient shouldBe createdClient
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

      val response = Await.result(
        Http()(classicSystem).singleRequest(
          HttpRequest(
            uri = s"$serviceURL/non-existing-client-id/keys",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, data)
          )
        ),
        Duration.Inf
      )

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

      val response = Await.result(
        Http()(classicSystem).singleRequest(
          HttpRequest(
            uri = s"$serviceURL/${clientId.toString}/keys",
            method = HttpMethods.POST,
            entity = HttpEntity(ContentTypes.`application/json`, data)
          )
        ),
        Duration.Inf
      )

      response.status shouldBe StatusCodes.Created
    }
  }

}
