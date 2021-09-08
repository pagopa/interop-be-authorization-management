package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import it.pagopa.pdnd.interop.uservice.keymanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

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

      val response =
        request(uri = s"$serviceURL/clients/non-existing-client-id/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.BadRequest
    }

    "fail if key operator has not been added to client" in {
      val clientId    = UUID.fromString("d68e1f97-5185-4319-89c7-2c8224a39641")
      val agreementId = UUID.fromString("2d756c4c-6ee7-4465-84e4-644a38d7aaa3")

      createClient(clientId, agreementId)

      val data =
        s"""
           |[
           |  {
           |    "operatorId": "2ce51cdd-ae78-4829-89cc-39e363270b53",
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "sig",
           |    "alg": "123"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.BadRequest
    }

    "succeed" in {
      val clientId    = UUID.fromString("638f76d2-39b3-47f2-bd65-a1e6094d35e9")
      val agreementId = UUID.fromString("5d2ca309-8871-412f-8073-3ab1da0148b7")
      val operatorId  = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6")

      createClient(clientId, agreementId)
      addOperator(clientId, operatorId)

      val data =
        s"""
           |[
           |  {
           |    "operatorId": "$operatorId",
           |    "key": "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF0WGxFTVAwUmEvY0dST050UmliWgppa1FhclUvY2pqaUpDTmNjMFN1dUtYUll2TGRDSkVycEt1UWNSZVhLVzBITGNCd3RibmRXcDhWU25RbkhUY0FpCm9rL0srSzhLblE3K3pEVHlSaTZXY3JhK2dtQi9KanhYeG9ZbjlEbFpBc2tjOGtDYkEvdGNnc1lsL3BmdDJ1YzAKUnNRdEZMbWY3cWVIYzQxa2dpOHNKTjdBbDJuYmVDb3EzWGt0YnBnQkVPcnZxRmttMkNlbG9PKzdPN0l2T3dzeQpjSmFiZ1p2Z01aSm4zeWFMeGxwVGlNanFtQjc5QnJwZENMSHZFaDhqZ2l5djJ2YmdwWE1QTlY1YXhaWmNrTnpRCnhNUWhwRzh5Y2QzaGJrV0s1b2ZkdXMwNEJ0T0c3ejBmbDNnVFp4czdOWDJDVDYzT0RkcnZKSFpwYUlqbks1NVQKbFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t",
           |    "use": "sig",
           |    "alg": "123"
           |  }
           |]
           |""".stripMargin

      val response =
        request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

      response.status shouldBe StatusCodes.Created
    }
  }

}
