package it.pagopa.interop.authorizationmanagement.projection.cqrs

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.interop.authorizationmanagement.ItCqrsSpec
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.utils.ClientAdapters.ClientWrapper
import it.pagopa.interop.authorizationmanagement.utils.JsonFormats._
import spray.json._

import java.util.UUID

class CqrsProjectionSpec extends ItCqrsSpec {

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
      val purpose1_1 = addPurposeState(clientId1, makePurposeSeed(eServiceId = eServiceId), UUID.randomUUID())
      val purpose1_2 = addPurposeState(clientId1, makePurposeSeed(eServiceId = eServiceId), UUID.randomUUID())
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

//    "succeed for event ClientAdded" in {
//
//      val clientId       = UUID.randomUUID()
//      val consumerId     = UUID.randomUUID()
//      val relationshipId = UUID.randomUUID()
//      val purposeSeed    = PurposeSeed(
//        ClientStatesChainSeed(
//          eservice = ClientEServiceDetailsSeed(
//            eserviceId = UUID.randomUUID(),
//            descriptorId = UUID.randomUUID(),
//            state = ClientComponentState.ACTIVE,
//            audience = Seq("aud1"),
//            voucherLifespan = 1000
//          ),
//          agreement = ClientAgreementDetailsSeed(
//            eserviceId = UUID.randomUUID(),
//            consumerId = UUID.randomUUID(),
//            agreementId = UUID.randomUUID(),
//            state = ClientComponentState.ACTIVE
//          ),
//          purpose = ClientPurposeDetailsSeed(
//            purposeId = UUID.randomUUID(),
//            versionId = UUID.randomUUID(),
//            state = ClientComponentState.INACTIVE
//          )
//        )
//      )
//
//      createClient(clientId, consumerId)
//      val client  = addRelationship(clientId, relationshipId)
//      val purpose = addPurposeState(clientId, purposeSeed, UUID.randomUUID())
//
//      //      println("-------------------------------------------------------")
//      //      println(client)
//      //      println("-------------------------------------------------------")
//
//      val expectedData = client.copy(purposes = Seq(purpose)).toPersistent
//
//      val persisted = find[PersistentClient](clientId.toString).futureValue
//
//      expectedData shouldBe persisted
//
//      //      val es = find[JsObject](clientId.toString).futureValue
//      //      println("-------------------------------------------------------")
//      //      println(es.prettyPrint)
//      //      println("-------------------------------------------------------")
//
//      //      Thread.sleep(3000)
//      //      val projectedEntity = mongodbClient
//      //        .getDatabase(mongoDbConfig.dbName)
//      //        .getCollection(mongoDbConfig.collectionName)
//      //        .find(Filters.eq("data.id", clientId.toString))
//      //
//      //      println("-------------------------------------------------------")
//      //      val fields =
//      //        projectedEntity.toFuture().futureValue.head.toJson().parseJson.asJsObject.getFields("data", "metadata")
//      //      fields match {
//      //        case data :: metadata :: Nil =>
//      //          val persistentClient = data.convertTo[PersistentClient]
//      //          val cqrsMetadata     = metadata.convertTo[CqrsMetadata]
//      //          println(persistentClient)
//      //          println(cqrsMetadata)
//      //
//      //          expectedData shouldBe persistentClient
//      //          assert(cqrsMetadata.sourceEvent.persistenceId.nonEmpty)
//      //          assert(cqrsMetadata.sourceEvent.sequenceNr >= 0)
//      //          assert(cqrsMetadata.sourceEvent.timestamp > 0)
//      //        case _                       => fail(s"Unexpected number of fields ${fields.size}. Content: $fields")
//      //      }
//      //      println("-------------------------------------------------------")
//    }
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
        ClientPurposeDetailsSeed(purposeId = purposeId, versionId = versionId, state = ClientComponentState.INACTIVE)
    )
  )
}
