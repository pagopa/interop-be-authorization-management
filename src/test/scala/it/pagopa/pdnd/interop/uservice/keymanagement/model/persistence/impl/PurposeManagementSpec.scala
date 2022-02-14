package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.keymanagement.model._
import it.pagopa.pdnd.interop.uservice.keymanagement.{SpecConfiguration, SpecHelper}
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json.enrichAny

import java.util.UUID

class PurposeManagementSpec
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

  "Purpose" should {

    "be added successfully" in {
      val clientId   = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val purposeId  = UUID.randomUUID()

      val statesChainId      = UUID.randomUUID()
      val eServiceDetailsId  = UUID.randomUUID()
      val agreementDetailsId = UUID.randomUUID()
      val purposeDetailsId   = UUID.randomUUID()

      createClient(clientId, consumerId)

      (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()
      (() => mockUUIDSupplier.get).expects().returning(eServiceDetailsId).once()
      (() => mockUUIDSupplier.get).expects().returning(agreementDetailsId).once()
      (() => mockUUIDSupplier.get).expects().returning(purposeDetailsId).once()

      val expected = Purpose(
        purposeId = purposeId,
        states = ClientStatesChain(
          id = statesChainId,
          eservice = ClientEServiceDetails(
            id = eServiceDetailsId,
            state = ClientComponentState.ACTIVE,
            audience = "some.audience",
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetails(id = agreementDetailsId, state = ClientComponentState.INACTIVE),
          purpose = ClientPurposeDetails(id = purposeDetailsId, state = ClientComponentState.ACTIVE)
        )
      )

      val payload = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            state = ClientComponentState.ACTIVE,
            audience = "some.audience",
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(state = ClientComponentState.INACTIVE),
          purpose = ClientPurposeDetailsSeed(state = ClientComponentState.ACTIVE)
        )
      )

      val response =
        request(
          uri = s"$serviceURL/clients/$clientId/purposes",
          method = HttpMethods.POST,
          data = Some(payload.toJson.prettyPrint)
        )

      response.status shouldBe StatusCodes.OK
      val addedPurpose = Unmarshal(response).to[Purpose].futureValue

      addedPurpose shouldBe expected
    }

    "fail if client does not exist" in {
      val clientId  = UUID.randomUUID()
      val purposeId = UUID.randomUUID()

      val statesChainId      = UUID.randomUUID()
      val eServiceDetailsId  = UUID.randomUUID()
      val agreementDetailsId = UUID.randomUUID()
      val purposeDetailsId   = UUID.randomUUID()

      (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()
      (() => mockUUIDSupplier.get).expects().returning(eServiceDetailsId).once()
      (() => mockUUIDSupplier.get).expects().returning(agreementDetailsId).once()
      (() => mockUUIDSupplier.get).expects().returning(purposeDetailsId).once()

      val payload = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            state = ClientComponentState.ACTIVE,
            audience = "some.audience",
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(state = ClientComponentState.INACTIVE),
          purpose = ClientPurposeDetailsSeed(state = ClientComponentState.ACTIVE)
        )
      )

      val response =
        request(
          uri = s"$serviceURL/clients/$clientId/purposes",
          method = HttpMethods.POST,
          data = Some(payload.toJson.prettyPrint)
        )

      response.status shouldBe StatusCodes.NotFound
      val problem = Unmarshal(response).to[Problem].futureValue

      problem.errors.head.code shouldBe "006-0002"
    }

  }

}
