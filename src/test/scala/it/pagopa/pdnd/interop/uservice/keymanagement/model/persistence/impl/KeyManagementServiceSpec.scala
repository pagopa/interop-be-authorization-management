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

  "Client creation" should {

    "succeed" in {
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

  }

//  "Key creation should" {
//    "fail if client does not exist" in {
//    }
//
//    "succeed if client exists" in {
//
//    }
//  }

}
