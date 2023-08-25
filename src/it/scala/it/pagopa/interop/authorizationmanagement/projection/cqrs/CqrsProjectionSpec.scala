package it.pagopa.interop.authorizationmanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.model.HttpMethods
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.key.{PersistentKey, PersistentKeyUse}
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.model.persistence.KeyAdapters._
import it.pagopa.interop.authorizationmanagement.utils.ClientAdapters.ClientWrapper
import it.pagopa.interop.authorizationmanagement.utils.JsonFormats._
import it.pagopa.interop.authorizationmanagement.{ItSpecConfiguration, ItSpecHelper}
import spray.json._

import java.util.UUID

class CqrsProjectionSpec extends ScalaTestWithActorTestKit(ItSpecConfiguration.config) with ItSpecHelper {

  "Projection" should {
    "succeed for event ClientAdded" in {
      val clientId   = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      val client = createClient(clientId, consumerId)

      val expectedData = client.toPersistent

      val persisted = findOne[PersistentClient](clientId.toString).futureValue

      expectedData shouldBe persisted
    }

    "succeed for event ClientDeleted" in {
      val clientId   = UUID.randomUUID()
      val consumerId = UUID.randomUUID()

      createClient(clientId, consumerId)
      request(uri = s"$serviceURL/clients/$clientId", method = HttpMethods.DELETE)

      val persisted = find[PersistentClient](clientId.toString).futureValue

      persisted shouldBe empty
    }

    "succeed for event RelationshipAdded" in {
      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      createClient(clientId, consumerId)
      val client = addRelationship(clientId, relationshipId)

      val expectedData = client.toPersistent

      val persisted = findOne[PersistentClient](clientId.toString).futureValue

      expectedData shouldBe persisted
    }

    "succeed for event RelationshipRemoved" in {
      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      createClient(clientId, consumerId)
      addRelationship(clientId, relationshipId)
      request(uri = s"$serviceURL/clients/$clientId/relationships/$relationshipId", method = HttpMethods.DELETE)

      val persisted = findOne[PersistentClient](clientId.toString).futureValue

      persisted.relationships shouldBe empty
    }

    "succeed for event KeysAdded" in {
      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      val keySeed = Key(
        kid = "kid",
        relationshipId = relationshipId,
        encodedPem = generateEncodedKey(),
        use = KeyUse.SIG,
        algorithm = "123",
        name = "IT Key",
        createdAt = timestamp
      )

      createClient(clientId, consumerId)
      addRelationship(clientId, relationshipId)
      val keysResponse = createKey(clientId, keySeed)

      val persistedClient = findOne[JsObject](clientId.toString).futureValue

      val persistedKeys = persistedClient.getFields("keys")
      assert(persistedKeys.nonEmpty)

      val persistentKey = persistedKeys.head.convertTo[Seq[PersistentKey]]

      val expected = Seq(
        PersistentKey(
          relationshipId = relationshipId,
          kid = keysResponse.head.kid,
          name = keySeed.name,
          encodedPem = keySeed.encodedPem,
          algorithm = keySeed.algorithm,
          use = PersistentKeyUse.fromApi(keySeed.use),
          createdAt = persistentKey.head.createdAt
        )
      )

      expected shouldBe persistentKey
    }

    "succeed for event KeyDeleted" in {
      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()

      createClient(clientId, consumerId)
      addRelationship(clientId, relationshipId)
      val keysResponse1 = createKey(clientId, relationshipId)
      val keysResponse2 = createKey(clientId, relationshipId)
      val kid1          = keysResponse1.head.kid
      val kid2          = keysResponse2.head.kid

      request(uri = s"$serviceURL/clients/$clientId/keys/$kid1", method = HttpMethods.DELETE)

      val persistedClient = findOne[JsObject](clientId.toString).futureValue
      val persistedKeys   = persistedClient.getFields("keys")

      assert(persistedKeys.size == 1)

      persistedKeys.flatMap(_.convertTo[Seq[PersistentKey]].map(_.kid)) shouldBe Seq(kid2)
    }

    "succeed for event ClientPurposeAdded" in {
      val clientId    = UUID.randomUUID()
      val consumerId  = UUID.randomUUID()
      val purposeSeed = makePurposeSeed()

      val client  = createClient(clientId, consumerId)
      val purpose = addPurposeState(clientId, purposeSeed, UUID.randomUUID())

      val expectedData = client.copy(purposes = Seq(purpose)).toPersistent

      val persisted = findOne[PersistentClient](clientId.toString).futureValue

      expectedData shouldBe persisted
    }

    "succeed for event ClientPurposeRemoved" in {
      val clientId     = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val purposeId    = UUID.randomUUID()
      val purposeSeed1 = makePurposeSeed(purposeId = purposeId)
      val purposeSeed2 = makePurposeSeed()

      createClient(clientId, consumerId)
      addPurposeState(clientId, purposeSeed1, UUID.randomUUID())
      addPurposeState(clientId, purposeSeed2, UUID.randomUUID())
      request(uri = s"$serviceURL/clients/$clientId/purposes/$purposeId", method = HttpMethods.DELETE)

      val persisted = findOne[PersistentClient](clientId.toString).futureValue

      persisted.purposes.map(_.purpose.purposeId) shouldBe Seq(purposeSeed2.states.purpose.purposeId)
    }

    "succeed for event EServiceStateUpdated" in {
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()

      val newDescriptorId = UUID.randomUUID()
      val updatedState    = ClientEServiceDetails(
        eserviceId = eServiceId,
        descriptorId = newDescriptorId,
        state = ClientComponentState.INACTIVE,
        audience = Seq("aud2", "aud3"),
        voucherLifespan = 333
      )

      val client1    = createClient(clientId1, consumerId)
      val client2    = createClient(clientId2, consumerId)
      val purpose1_1 = addPurposeState(
        clientId1,
        makePurposeSeed(eServiceId = eServiceId, descriptorId = newDescriptorId),
        UUID.randomUUID()
      )
      val purpose1_2 = addPurposeState(
        clientId1,
        makePurposeSeed(eServiceId = eServiceId, descriptorId = newDescriptorId),
        UUID.randomUUID()
      )
      val purpose1_3 = addPurposeState(clientId1, makePurposeSeed(), UUID.randomUUID())
      val purpose2_1 = addPurposeState(clientId2, makePurposeSeed(), UUID.randomUUID())

      val payload = ClientEServiceDetailsUpdate(
        state = ClientComponentState.INACTIVE,
        descriptorId = newDescriptorId,
        audience = Seq("aud2", "aud3"),
        voucherLifespan = 333
      ).toJson.compactPrint

      request(uri = s"$serviceURL/bulk/eservices/$eServiceId/state", method = HttpMethods.POST, data = Some(payload))

      val expectedClient1 = client1
        .copy(purposes =
          Seq(
            purpose1_1.copy(states = purpose1_1.states.copy(eservice = updatedState)),
            purpose1_2.copy(states = purpose1_2.states.copy(eservice = updatedState)),
            purpose1_3
          )
        )
        .toPersistent

      val expectedClient2 = client2.copy(purposes = Seq(purpose2_1)).toPersistent

      val persisted1 = findOne[PersistentClient](clientId1.toString).futureValue
      val persisted2 = findOne[PersistentClient](clientId2.toString).futureValue

      persisted1 shouldBe expectedClient1
      persisted2 shouldBe expectedClient2
    }

    "succeed for event AgreementStateUpdated" in {
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val eServiceId = UUID.randomUUID()

      val newAgreementId = UUID.randomUUID()
      val updatedState   = ClientAgreementDetails(
        eserviceId = eServiceId,
        consumerId = consumerId,
        agreementId = newAgreementId,
        state = ClientComponentState.INACTIVE
      )

      val client1    = createClient(clientId1, consumerId)
      val client2    = createClient(clientId2, consumerId)
      val purpose1_1 =
        addPurposeState(clientId1, makePurposeSeed(eServiceId = eServiceId, consumerId = consumerId), UUID.randomUUID())
      val purpose1_2 =
        addPurposeState(clientId1, makePurposeSeed(eServiceId = eServiceId, consumerId = consumerId), UUID.randomUUID())
      val purpose1_3 = addPurposeState(clientId1, makePurposeSeed(), UUID.randomUUID())
      val purpose2_1 = addPurposeState(clientId2, makePurposeSeed(), UUID.randomUUID())

      val payload = ClientAgreementDetailsUpdate(
        state = ClientComponentState.INACTIVE,
        agreementId = newAgreementId
      ).toJson.compactPrint

      request(
        uri = s"$serviceURL/bulk/agreements/eserviceId/$eServiceId/consumerId/$consumerId/state",
        method = HttpMethods.POST,
        data = Some(payload)
      )

      val expectedClient1 = client1
        .copy(purposes =
          Seq(
            purpose1_1.copy(states = purpose1_1.states.copy(agreement = updatedState)),
            purpose1_2.copy(states = purpose1_2.states.copy(agreement = updatedState)),
            purpose1_3
          )
        )
        .toPersistent

      val expectedClient2 = client2.copy(purposes = Seq(purpose2_1)).toPersistent

      val persisted1 = findOne[PersistentClient](clientId1.toString).futureValue
      val persisted2 = findOne[PersistentClient](clientId2.toString).futureValue

      persisted1 shouldBe expectedClient1
      persisted2 shouldBe expectedClient2
    }

    "succeed for event AgreementAndEServiceStatesUpdated" in {
      val clientId1    = UUID.randomUUID()
      val consumerId   = UUID.randomUUID()
      val eServiceId   = UUID.randomUUID()
      val agreementId  = UUID.randomUUID()
      val descriptorId = UUID.randomUUID()

      val newAgreementId  = UUID.randomUUID()
      val newDescriptorId = UUID.randomUUID()

      val updatedAgreementState = ClientAgreementDetails(
        eserviceId = eServiceId,
        consumerId = consumerId,
        agreementId = newAgreementId,
        state = ClientComponentState.INACTIVE
      )

      val updatedEServiceState = ClientEServiceDetails(
        eserviceId = eServiceId,
        descriptorId = newDescriptorId,
        state = ClientComponentState.INACTIVE,
        audience = Seq("aud2"),
        voucherLifespan = 1000
      )

      val client1 = createClient(clientId1, consumerId)

      val purpose1_1 =
        addPurposeState(
          clientId1,
          makePurposeSeed(
            eServiceId = eServiceId,
            descriptorId = descriptorId,
            agreementId = agreementId,
            consumerId = consumerId
          ),
          UUID.randomUUID()
        )
      val purpose1_2 =
        addPurposeState(
          clientId1,
          makePurposeSeed(
            eServiceId = eServiceId,
            descriptorId = descriptorId,
            agreementId = agreementId,
            consumerId = consumerId
          ),
          UUID.randomUUID()
        )
      val purpose1_3 = addPurposeState(clientId1, makePurposeSeed(), UUID.randomUUID())

      val payload = ClientAgreementAndEServiceDetailsUpdate(
        agreementId = newAgreementId,
        agreementState = ClientComponentState.INACTIVE,
        descriptorId = newDescriptorId,
        audience = Seq("aud2"),
        voucherLifespan = 1000,
        eserviceState = ClientComponentState.INACTIVE
      ).toJson.compactPrint

      request(
        uri = s"$serviceURL/bulk/agreements/eservices/eserviceId/$eServiceId/consumerId/$consumerId/state",
        method = HttpMethods.POST,
        data = Some(payload)
      )

      val expectedClient1 = client1
        .copy(purposes =
          Seq(
            purpose1_1.copy(states =
              purpose1_1.states.copy(agreement = updatedAgreementState, eservice = updatedEServiceState)
            ),
            purpose1_2.copy(states =
              purpose1_2.states.copy(agreement = updatedAgreementState, eservice = updatedEServiceState)
            ),
            purpose1_3
          )
        )
        .toPersistent

      val persisted1 = findOne[PersistentClient](clientId1.toString).futureValue

      persisted1 shouldBe expectedClient1
    }

    "succeed for event PurposeStateUpdated" in {
      val clientId1  = UUID.randomUUID()
      val clientId2  = UUID.randomUUID()
      val consumerId = UUID.randomUUID()
      val purposeId  = UUID.randomUUID()

      val newVersionId = UUID.randomUUID()
      val updatedState =
        ClientPurposeDetails(purposeId = purposeId, versionId = newVersionId, state = ClientComponentState.INACTIVE)

      val client1    = createClient(clientId1, consumerId)
      val client2    = createClient(clientId2, consumerId)
      val purpose1_1 = addPurposeState(clientId1, makePurposeSeed(purposeId = purposeId), UUID.randomUUID())
      val purpose1_2 = addPurposeState(clientId1, makePurposeSeed(), UUID.randomUUID())
      val purpose2_1 = addPurposeState(clientId2, makePurposeSeed(), UUID.randomUUID())

      val payload =
        ClientPurposeDetailsUpdate(state = ClientComponentState.INACTIVE, versionId = newVersionId).toJson.compactPrint

      request(uri = s"$serviceURL/bulk/purposes/$purposeId/state", method = HttpMethods.POST, data = Some(payload))

      val expectedClient1 = client1
        .copy(purposes = Seq(purpose1_1.copy(states = purpose1_1.states.copy(purpose = updatedState)), purpose1_2))
        .toPersistent

      val expectedClient2 = client2.copy(purposes = Seq(purpose2_1)).toPersistent

      val persisted1 = findOne[PersistentClient](clientId1.toString).futureValue
      val persisted2 = findOne[PersistentClient](clientId2.toString).futureValue

      persisted1 shouldBe expectedClient1
      persisted2 shouldBe expectedClient2
    }

  }

  def makePurposeSeed(
    eServiceId: UUID = UUID.randomUUID(),
    descriptorId: UUID = UUID.randomUUID(),
    consumerId: UUID = UUID.randomUUID(),
    agreementId: UUID = UUID.randomUUID(),
    purposeId: UUID = UUID.randomUUID(),
    versionId: UUID = UUID.randomUUID()
  ): PurposeSeed = PurposeSeed(
    ClientStatesChainSeed(
      eservice = ClientEServiceDetailsSeed(
        eserviceId = eServiceId,
        descriptorId = descriptorId,
        state = ClientComponentState.ACTIVE,
        audience = Seq("aud1"),
        voucherLifespan = 1000
      ),
      agreement = ClientAgreementDetailsSeed(
        eserviceId = eServiceId,
        consumerId = consumerId,
        agreementId = agreementId,
        state = ClientComponentState.ACTIVE
      ),
      purpose =
        ClientPurposeDetailsSeed(purposeId = purposeId, versionId = versionId, state = ClientComponentState.ACTIVE)
    )
  )
}
