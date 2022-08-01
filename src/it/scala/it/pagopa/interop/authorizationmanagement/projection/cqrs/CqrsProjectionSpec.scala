package it.pagopa.interop.authorizationmanagement.projection.cqrs

import akka.http.scaladsl.model.HttpMethods
import it.pagopa.interop.authorizationmanagement.ItCqrsSpec
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.utils.ClientAdapters.ClientWrapper

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
      val purposeSeed = PurposeSeed(
        ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = UUID.randomUUID(),
            descriptorId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE,
            audience = Seq("aud1"),
            voucherLifespan = 1000
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = UUID.randomUUID(),
            consumerId = UUID.randomUUID(),
            agreementId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = UUID.randomUUID(),
            versionId = UUID.randomUUID(),
            state = ClientComponentState.INACTIVE
          )
        )
      )

      val client  = createClient(clientId, consumerId)
      val purpose = addPurposeState(clientId, purposeSeed, UUID.randomUUID())

      val expectedData = client.copy(purposes = Seq(purpose)).toPersistent

      val persisted = findOne[PersistentClient](clientId.toString).futureValue

      expectedData shouldBe persisted
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
}
