package it.pagopa.interop.authorizationmanagement.model.persistence.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import it.pagopa.interop.authorizationmanagement.api.impl._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.persistence.client.{
  PersistentClientAgreementDetails,
  PersistentClientEServiceDetails,
  PersistentClientPurposeDetails
}
import it.pagopa.interop.authorizationmanagement.{SpecConfiguration, SpecHelper}
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
      val clientId         = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()
      val purposeId        = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val descriptorId     = UUID.randomUUID()
      val agreementId      = UUID.randomUUID()
      val purposeVersionId = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()

      createClient(clientId, consumerId)

      (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()

      val expected = Purpose(
        purposeId = purposeId,
        states = ClientStatesChain(
          id = statesChainId,
          eservice = ClientEServiceDetails(
            eserviceId = eServiceId,
            descriptorId = descriptorId,
            state = ClientComponentState.ACTIVE,
            audience = Seq("some.audience"),
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetails(
            eserviceId = eServiceId,
            consumerId = consumerId,
            agreementId = agreementId,
            state = ClientComponentState.INACTIVE
          ),
          purpose = ClientPurposeDetails(
            purposeId = purposeId,
            versionId = purposeVersionId,
            state = ClientComponentState.ACTIVE
          )
        )
      )

      val payload = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            descriptorId = descriptorId,
            state = ClientComponentState.ACTIVE,
            audience = Seq("some.audience"),
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = eServiceId,
            consumerId = consumerId,
            agreementId = agreementId,
            state = ClientComponentState.INACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = purposeId,
            versionId = purposeVersionId,
            state = ClientComponentState.ACTIVE
          )
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
      val clientId         = UUID.randomUUID()
      val purposeId        = UUID.randomUUID()
      val purposeVersionId = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()
      val descriptorId     = UUID.randomUUID()
      val agreementId      = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()

      (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()

      val payload = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            descriptorId = descriptorId,
            state = ClientComponentState.ACTIVE,
            audience = Seq("some.audience"),
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = eServiceId,
            consumerId = consumerId,
            agreementId = agreementId,
            state = ClientComponentState.INACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = purposeId,
            versionId = purposeVersionId,
            state = ClientComponentState.ACTIVE
          )
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

  "Purpose removal" should {

    "succeed" in {
      val clientId         = UUID.randomUUID()
      val consumerId       = UUID.randomUUID()
      val purposeId        = UUID.randomUUID()
      val eServiceId       = UUID.randomUUID()
      val descriptorId     = UUID.randomUUID()
      val agreementId      = UUID.randomUUID()
      val purposeVersionId = UUID.randomUUID()

      val statesChainId = UUID.randomUUID()
      val payload       = PurposeSeed(
        purposeId = purposeId,
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = eServiceId,
            descriptorId = descriptorId,
            state = ClientComponentState.ACTIVE,
            audience = Seq("some.audience"),
            voucherLifespan = 10
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = eServiceId,
            consumerId = consumerId,
            agreementId = agreementId,
            state = ClientComponentState.INACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = purposeId,
            versionId = purposeVersionId,
            state = ClientComponentState.ACTIVE
          )
        )
      )

      createClient(clientId, consumerId)
      addPurposeState(clientId, payload, statesChainId)

      val response =
        request(uri = s"$serviceURL/clients/$clientId/purposes/$purposeId", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NoContent

      retrieveClient(clientId).purposes shouldBe empty
    }

    "fail if client does not exist" in {
      val clientId  = UUID.randomUUID()
      val purposeId = UUID.randomUUID()

      val response =
        request(uri = s"$serviceURL/clients/$clientId/purposes/$purposeId", method = HttpMethods.DELETE)

      response.status shouldBe StatusCodes.NotFound
      val problem = Unmarshal(response).to[Problem].futureValue

      problem.errors.head.code shouldBe "006-0002"
    }

  }

  "EService state update" should {

    "succeed" in {
      val clientId1    = UUID.randomUUID()
      val clientId2    = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val agreementId1 = UUID.randomUUID()

      val purposeId1        = UUID.randomUUID()
      val purposeId2        = UUID.randomUUID()
      val purposeId3        = UUID.randomUUID()
      val purposeVersionId1 = UUID.randomUUID()
      val eServiceId1       = UUID.randomUUID()
      val eServiceId2       = UUID.randomUUID()
      val descriptorId1     = UUID.randomUUID()

      val statesChainId1 = UUID.randomUUID()
      val statesChainId2 = UUID.randomUUID()
      val statesChainId3 = UUID.randomUUID()
      val statesChainId4 = UUID.randomUUID()

      // Seed
      val eService1Seed = ClientEServiceDetailsSeed(
        eserviceId = eServiceId1,
        descriptorId = descriptorId1,
        state = ClientComponentState.ACTIVE,
        audience = Seq("some.audience"),
        voucherLifespan = 10
      )
      val eService2Seed = eService1Seed.copy(eserviceId = eServiceId2)
      val agreementSeed = ClientAgreementDetailsSeed(
        eserviceId = eServiceId1,
        consumerId = consumerId,
        agreementId = agreementId1,
        state = ClientComponentState.ACTIVE
      )
      val purposeSeed   = ClientPurposeDetailsSeed(
        purposeId = purposeId1,
        versionId = purposeVersionId1,
        state = ClientComponentState.ACTIVE
      )

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
        audience = Seq("some.other.audience"),
        voucherLifespan = 50
      )

      val agreementDetails = PersistentClientAgreementDetails.fromSeed(agreementSeed).toApi
      val purposeDetails   = PersistentClientPurposeDetails.fromSeed(purposeSeed).toApi

      val expectedEService1State = ClientEServiceDetails(
        eserviceId = eServiceId1,
        descriptorId = descriptorId1,
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

  "Agreement state update" should {

    "succeed" in {
      val clientId1     = UUID.randomUUID()
      val clientId2     = UUID.randomUUID()
      val consumerId    = UUID.randomUUID()
      val agreementId1  = UUID.randomUUID()
      val agreementId2  = UUID.randomUUID()
      val eServiceId1   = UUID.randomUUID()
      val eServiceId2   = UUID.randomUUID()
      val descriptorId1 = UUID.randomUUID()

      val purposeId1        = UUID.randomUUID()
      val purposeId2        = UUID.randomUUID()
      val purposeId3        = UUID.randomUUID()
      val purposeVersionId1 = UUID.randomUUID()

      val statesChainId1 = UUID.randomUUID()
      val statesChainId2 = UUID.randomUUID()
      val statesChainId3 = UUID.randomUUID()
      val statesChainId4 = UUID.randomUUID()

      // Seed
      val eServiceSeed = ClientEServiceDetailsSeed(
        eserviceId = eServiceId1,
        descriptorId = descriptorId1,
        state = ClientComponentState.ACTIVE,
        audience = Seq("some.audience"),
        voucherLifespan = 10
      )
      val purposeSeed  = ClientPurposeDetailsSeed(
        purposeId = purposeId1,
        versionId = purposeVersionId1,
        state = ClientComponentState.ACTIVE
      )

      val agreementSeed1 = ClientAgreementDetailsSeed(
        eserviceId = eServiceId1,
        consumerId = consumerId,
        agreementId = agreementId1,
        state = ClientComponentState.ACTIVE
      )
      val agreementSeed2 = ClientAgreementDetailsSeed(
        eserviceId = eServiceId2,
        consumerId = consumerId,
        agreementId = agreementId2,
        state = ClientComponentState.ACTIVE
      )

      val purpose1Agreement1Seed = PurposeSeed(
        purposeId = purposeId1,
        states = ClientStatesChainSeed(eservice = eServiceSeed, agreement = agreementSeed1, purpose = purposeSeed)
      )
      val purpose2Agreement1Seed = PurposeSeed(
        purposeId = purposeId2,
        states = ClientStatesChainSeed(eservice = eServiceSeed, agreement = agreementSeed1, purpose = purposeSeed)
      )

      val purpose3Agreement2Seed = PurposeSeed(
        purposeId = purposeId3,
        states = ClientStatesChainSeed(eservice = eServiceSeed, agreement = agreementSeed2, purpose = purposeSeed)
      )
      // Seed

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)

      addPurposeState(clientId1, purpose1Agreement1Seed, statesChainId1)
      addPurposeState(clientId1, purpose3Agreement2Seed, statesChainId2)
      addPurposeState(clientId2, purpose1Agreement1Seed, statesChainId3)
      addPurposeState(clientId2, purpose2Agreement1Seed, statesChainId4)

      val updatePayload = ClientAgreementDetailsUpdate(state = ClientComponentState.INACTIVE)

      val eServiceDetails = PersistentClientEServiceDetails.fromSeed(eServiceSeed).toApi
      val purposeDetails  = PersistentClientPurposeDetails.fromSeed(purposeSeed).toApi

      val expectedAgreement1State =
        ClientAgreementDetails(
          eserviceId = eServiceId1,
          consumerId = consumerId,
          agreementId = agreementId1,
          state = updatePayload.state
        )

      val expectedClient1Purposes: Seq[Purpose] = Seq(
        Purpose(
          purposeId = purposeId1,
          states = ClientStatesChain(
            id = statesChainId1,
            eservice = eServiceDetails,
            agreement = expectedAgreement1State,
            purpose = purposeDetails
          )
        ),
        Purpose(
          purposeId = purpose3Agreement2Seed.purposeId,
          states = ClientStatesChain(
            id = statesChainId2,
            eservice = eServiceDetails,
            agreement = PersistentClientAgreementDetails.fromSeed(purpose3Agreement2Seed.states.agreement).toApi,
            purpose = purposeDetails
          )
        )
      )

      val expectedClient2Purposes: Seq[Purpose] = Seq(
        Purpose(
          purposeId = purposeId1,
          states = ClientStatesChain(
            id = statesChainId3,
            eservice = eServiceDetails,
            agreement = expectedAgreement1State,
            purpose = purposeDetails
          )
        ),
        Purpose(
          purposeId = purpose2Agreement1Seed.purposeId,
          states = ClientStatesChain(
            id = statesChainId4,
            eservice = eServiceDetails,
            agreement = expectedAgreement1State,
            purpose = purposeDetails
          )
        )
      )

      val response =
        request(
          uri = s"$serviceURL/bulk/agreements/eserviceId/$eServiceId1/consumerId/$consumerId/state",
          method = HttpMethods.POST,
          data = Some(updatePayload.toJson.prettyPrint)
        )

      response.status shouldBe StatusCodes.NoContent

      retrieveClient(clientId1).purposes should contain theSameElementsAs expectedClient1Purposes
      retrieveClient(clientId2).purposes should contain theSameElementsAs expectedClient2Purposes
    }

  }

  "Purpose state update" should {

    "succeed" in {
      val clientId1   = UUID.randomUUID()
      val clientId2   = UUID.randomUUID()
      val consumerId  = UUID.randomUUID()
      val agreementId = UUID.randomUUID()

      val purposeId1        = UUID.randomUUID()
      val purposeId2        = UUID.randomUUID()
      val purposeVersionId1 = UUID.randomUUID()
      val purposeVersionId2 = UUID.randomUUID()
      val eServiceId        = UUID.randomUUID()
      val descriptorId      = UUID.randomUUID()

      val statesChainId1 = UUID.randomUUID()
      val statesChainId2 = UUID.randomUUID()
      val statesChainId3 = UUID.randomUUID()
      val statesChainId4 = UUID.randomUUID()

      // Seed
      val eServiceSeed        = ClientEServiceDetailsSeed(
        eserviceId = eServiceId,
        descriptorId = descriptorId,
        state = ClientComponentState.ACTIVE,
        audience = Seq("some.audience"),
        voucherLifespan = 10
      )
      val purposeDetailsSeed1 = ClientPurposeDetailsSeed(
        purposeId = purposeId1,
        versionId = purposeVersionId1,
        state = ClientComponentState.ACTIVE
      )
      val purposeDetailsSeed2 = ClientPurposeDetailsSeed(
        purposeId = purposeId2,
        versionId = purposeVersionId2,
        state = ClientComponentState.ACTIVE
      )

      val agreementSeed = ClientAgreementDetailsSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        agreementId = agreementId,
        state = ClientComponentState.ACTIVE
      )

      val purposeSeed1 = PurposeSeed(
        purposeId = purposeId1,
        states =
          ClientStatesChainSeed(eservice = eServiceSeed, agreement = agreementSeed, purpose = purposeDetailsSeed1)
      )
      val purposeSeed2 = PurposeSeed(
        purposeId = purposeId2,
        states =
          ClientStatesChainSeed(eservice = eServiceSeed, agreement = agreementSeed, purpose = purposeDetailsSeed2)
      )
      // Seed

      createClient(clientId1, consumerId)
      createClient(clientId2, consumerId)

      addPurposeState(clientId1, purposeSeed1, statesChainId1)
      addPurposeState(clientId1, purposeSeed2, statesChainId2)
      addPurposeState(clientId2, purposeSeed1, statesChainId3)
      addPurposeState(clientId2, purposeSeed2, statesChainId4)

      val updatePayload = ClientPurposeDetailsUpdate(state = ClientComponentState.INACTIVE)

      val eServiceDetails  = PersistentClientEServiceDetails.fromSeed(eServiceSeed).toApi
      val agreementDetails = PersistentClientAgreementDetails.fromSeed(agreementSeed).toApi

      val expectedPurpose1State =
        ClientPurposeDetails(purposeId = purposeId1, versionId = purposeVersionId1, state = updatePayload.state)

      val expectedClient1Purposes: Seq[Purpose] = Seq(
        Purpose(
          purposeId = purposeId1,
          states = ClientStatesChain(
            id = statesChainId1,
            eservice = eServiceDetails,
            agreement = agreementDetails,
            purpose = expectedPurpose1State
          )
        ),
        Purpose(
          purposeId = purposeId2,
          states = ClientStatesChain(
            id = statesChainId2,
            eservice = eServiceDetails,
            agreement = agreementDetails,
            purpose = PersistentClientPurposeDetails.fromSeed(purposeSeed2.states.purpose).toApi
          )
        )
      )

      val expectedClient2Purposes: Seq[Purpose] = Seq(
        Purpose(
          purposeId = purposeId1,
          states = ClientStatesChain(
            id = statesChainId3,
            eservice = eServiceDetails,
            agreement = agreementDetails,
            purpose = expectedPurpose1State
          )
        ),
        Purpose(
          purposeId = purposeId2,
          states = ClientStatesChain(
            id = statesChainId4,
            eservice = eServiceDetails,
            agreement = agreementDetails,
            purpose = PersistentClientPurposeDetails.fromSeed(purposeSeed2.states.purpose).toApi
          )
        )
      )

      val response =
        request(
          uri = s"$serviceURL/bulk/purposes/$purposeId1/state",
          method = HttpMethods.POST,
          data = Some(updatePayload.toJson.prettyPrint)
        )

      response.status shouldBe StatusCodes.NoContent

      retrieveClient(clientId1).purposes should contain theSameElementsAs expectedClient1Purposes
      retrieveClient(clientId2).purposes should contain theSameElementsAs expectedClient2Purposes
    }

  }
}
