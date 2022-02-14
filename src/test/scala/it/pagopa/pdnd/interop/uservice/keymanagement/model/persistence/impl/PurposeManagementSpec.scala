package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.keymanagement.model._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.{
  PersistentClientAgreementDetails,
  PersistentClientEServiceDetails,
  PersistentClientPurposeDetails
}
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

  "Purpose addition" should {

    "succeed" in {
      val clientId    = UUID.randomUUID()
      val consumerId  = UUID.randomUUID()
      val purposeId   = UUID.randomUUID()
      val eServiceId  = UUID.randomUUID()
      val agreementId = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()

      createClient(clientId, consumerId)

      (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()

      val expected = Purpose(
        purposeId = purposeId,
        states = ClientStatesChain(
          id = statesChainId,
          eservice = ClientEServiceDetails(
            eserviceId = eServiceId,
            state = ClientComponentState.ACTIVE,
            audience = "some.audience",
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetails(agreementId = agreementId, state = ClientComponentState.INACTIVE),
          purpose = ClientPurposeDetails(purposeId = purposeId, state = ClientComponentState.ACTIVE)
        )
      )

      val payload = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            state = ClientComponentState.ACTIVE,
            audience = "some.audience",
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(agreementId = agreementId, state = ClientComponentState.INACTIVE),
          purpose = ClientPurposeDetailsSeed(purposeId = purposeId, state = ClientComponentState.ACTIVE)
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
      val clientId    = UUID.randomUUID()
      val purposeId   = UUID.randomUUID()
      val eServiceId  = UUID.randomUUID()
      val agreementId = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()

      (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()

      val payload = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            state = ClientComponentState.ACTIVE,
            audience = "some.audience",
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(agreementId = agreementId, state = ClientComponentState.INACTIVE),
          purpose = ClientPurposeDetailsSeed(purposeId = purposeId, state = ClientComponentState.ACTIVE)
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

  "EService state update" should {

    "succeed" in {
      val clientId1   = UUID.randomUUID()
      val clientId2   = UUID.randomUUID()
      val consumerId  = UUID.randomUUID()
      val agreementId = UUID.randomUUID()

      val purposeId1  = UUID.randomUUID()
      val purposeId2  = UUID.randomUUID()
      val purposeId3  = UUID.randomUUID()
      val eServiceId1 = UUID.randomUUID()
      val eServiceId2 = UUID.randomUUID()

      val statesChainId1 = UUID.randomUUID()
      val statesChainId2 = UUID.randomUUID()
      val statesChainId3 = UUID.randomUUID()
      val statesChainId4 = UUID.randomUUID()

      // Seed
      val eService1Seed = ClientEServiceDetailsSeed(
        eserviceId = eServiceId1,
        state = ClientComponentState.ACTIVE,
        audience = "some.audience",
        voucherLifespan = 10
      )
      val eService2Seed = eService1Seed.copy(eserviceId = eServiceId2)
      val agreementSeed = ClientAgreementDetailsSeed(agreementId = agreementId, state = ClientComponentState.ACTIVE)
      val purposeSeed   = ClientPurposeDetailsSeed(purposeId = purposeId1, state = ClientComponentState.ACTIVE)

      val purpose1EService1Seed = PurposeSeed(
        purposeId = purposeId1,
        states = ClientStatesChainSeed(eservice = eService1Seed, agreement = agreementSeed, purpose = purposeSeed)
      )
      val purpose2EService1Seed = PurposeSeed(
        purposeId = purposeId2,
        states = ClientStatesChainSeed(eservice = eService1Seed, agreement = agreementSeed, purpose = purposeSeed)
      )

      val purpose3EService2Seed = PurposeSeed(
        purposeId = purposeId3,
        states = ClientStatesChainSeed(eservice = eService2Seed, agreement = agreementSeed, purpose = purposeSeed)
      )
      // Seed

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)

      addPurposeState(clientId1, purpose1EService1Seed, statesChainId1)
      addPurposeState(clientId1, purpose3EService2Seed, statesChainId2)
      addPurposeState(clientId2, purpose1EService1Seed, statesChainId3)
      addPurposeState(clientId2, purpose2EService1Seed, statesChainId4)

      val updatePayload = ClientEServiceDetailsUpdate(
        state = ClientComponentState.INACTIVE,
        audience = "some.other.audience",
        voucherLifespan = 50
      )

      val agreementDetails = PersistentClientAgreementDetails.fromSeed(agreementSeed).toApi
      val purposeDetails   = PersistentClientPurposeDetails.fromSeed(purposeSeed).toApi

      val expectedEService1State = ClientEServiceDetails(
        eserviceId = eServiceId1,
        state = updatePayload.state,
        audience = updatePayload.audience,
        voucherLifespan = updatePayload.voucherLifespan
      )

      val expectedClient1Purposes: Seq[Purpose] = Seq(
        Purpose(
          purposeId = purposeId1,
          states = ClientStatesChain(
            id = statesChainId1,
            eservice = expectedEService1State,
            agreement = agreementDetails,
            purpose = purposeDetails
          )
        ),
        Purpose(
          purposeId = purpose3EService2Seed.purposeId,
          states = ClientStatesChain(
            id = statesChainId2,
            eservice = PersistentClientEServiceDetails.fromSeed(purpose3EService2Seed.states.eservice).toApi,
            agreement = agreementDetails,
            purpose = purposeDetails
          )
        )
      )

      val expectedClient2Purposes: Seq[Purpose] = Seq(
        Purpose(
          purposeId = purposeId1,
          states = ClientStatesChain(
            id = statesChainId3,
            eservice = expectedEService1State,
            agreement = agreementDetails,
            purpose = purposeDetails
          )
        ),
        Purpose(
          purposeId = purpose2EService1Seed.purposeId,
          states = ClientStatesChain(
            id = statesChainId4,
            eservice = expectedEService1State,
            agreement = agreementDetails,
            purpose = purposeDetails
          )
        )
      )

      val response =
        request(
          uri = s"$serviceURL/bulk/eservices/$eServiceId1/state",
          method = HttpMethods.POST,
          data = Some(updatePayload.toJson.prettyPrint)
        )

      response.status shouldBe StatusCodes.NoContent

      retrieveClient(clientId1).purposes should contain theSameElementsAs expectedClient1Purposes
      retrieveClient(clientId2).purposes should contain theSameElementsAs expectedClient2Purposes
    }

  }

}
