package it.pagopa.interop.authorizationmanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.DockerComposeContainer.ComposeFile
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.authorizationmanagement.model.{
  ClientAgreementDetailsSeed,
  ClientComponentState,
  ClientEServiceDetailsSeed,
  ClientPurposeDetailsSeed,
  ClientStatesChainSeed,
  PurposeSeed
}
import it.pagopa.interop.authorizationmanagement.model.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.utils.ClientAdapters.ClientWrapper
import it.pagopa.interop.authorizationmanagement.{ItSpecConfiguration, ItSpecHelper}
import it.pagopa.interop.commons.cqrs.model.{CqrsMetadata, MongoDbConfig}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ConnectionString, Document, MongoClient, MongoClientSettings}
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.io.File
import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// Testing only the projection is not possible (https://github.com/akka/akka-projection/issues/454)

class CqrsProjectionSpec
    extends ScalaTestWithActorTestKit(ItSpecConfiguration.config)
    with ItSpecHelper
    with AnyWordSpecLike
    with TestContainersForAll {

  private var internalMongodbClient: Option[MongoClient] = None
  val mongoDbConfig: MongoDbConfig                       = ApplicationConfiguration.mongoDb

  implicit val mongodbOpTimeout: FiniteDuration = 5.seconds
  val mongodbOpWaitTime: FiniteDuration         = 100.millis

  override type Containers = DockerComposeContainer

  override def startContainers(): Containers =
    DockerComposeContainer.Def(ComposeFile(Left(new File("src/it/resources/docker-compose-it.yaml")))).start()

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)

    Thread.sleep(5000)

    internalMongodbClient = Some(
      MongoClient(
        MongoClientSettings
          .builder()
          .applyConnectionString(new ConnectionString(mongoDbConfig.connectionString))
          .codecRegistry(DEFAULT_CODEC_REGISTRY)
          .streamFactoryFactory(NettyStreamFactoryFactory())
          .build()
      )
    )

    startServer()
  }

  def mongodbClient: MongoClient = internalMongodbClient match {
    case Some(client) => client
    case None         => throw new Exception("MongoDB client not yet initialized")
  }

  def find[T](id: String)(implicit timeout: FiniteDuration, jsonReader: JsonReader[T]): Future[T] = for {
    results <- mongodbClient
      .getDatabase(mongoDbConfig.dbName)
      .getCollection(mongoDbConfig.collectionName)
      .find(Filters.eq("data.id", id))
      .toFuture()
    value   <-
      if (results.isEmpty) {
        Future.successful(Thread.sleep(mongodbOpWaitTime.toMillis))
        find[T](id)(timeout - mongodbOpWaitTime, jsonReader)
      } else Future.successful(extractData[T](results.head))
  } yield value

  private def extractData[T: JsonReader](document: Document): T = {
    val fields = document.toJson().parseJson.asJsObject.getFields("data", "metadata")
    fields match {
      case data :: metadata :: Nil =>
        val cqrsMetadata = metadata.convertTo[CqrsMetadata]

        assert(cqrsMetadata.sourceEvent.persistenceId.nonEmpty)
        assert(cqrsMetadata.sourceEvent.sequenceNr >= 0)
        assert(cqrsMetadata.sourceEvent.timestamp > 0)

        data.convertTo[T]
      case _                       => fail(s"Unexpected number of fields ${fields.size}. Content: $fields")
    }
  }

  override def beforeContainersStop(containers: Containers): Unit = {
    shutdownServer()
//    Thread.sleep(40000)
    super.afterContainersStart(containers)
  }

  "request" should {
    "succeed" in {

      val clientId       = UUID.randomUUID()
      val consumerId     = UUID.randomUUID()
      val relationshipId = UUID.randomUUID()
      val purposeSeed    = PurposeSeed(
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

      val client = createClient(clientId, consumerId)
      addRelationship(clientId, relationshipId)
      addPurposeState(clientId, purposeSeed, UUID.randomUUID())

//      println("-------------------------------------------------------")
//      println(client)
//      println("-------------------------------------------------------")

      val expectedData = client.toPersistent

      val persisted = find[PersistentClient](clientId.toString).futureValue

      expectedData shouldBe persisted

//      val es = find[JsObject](clientId.toString).futureValue
//      println("-------------------------------------------------------")
//      println(es.prettyPrint)
//      println("-------------------------------------------------------")

      //      Thread.sleep(3000)
//      val projectedEntity = mongodbClient
//        .getDatabase(mongoDbConfig.dbName)
//        .getCollection(mongoDbConfig.collectionName)
//        .find(Filters.eq("data.id", clientId.toString))
//
//      println("-------------------------------------------------------")
//      val fields =
//        projectedEntity.toFuture().futureValue.head.toJson().parseJson.asJsObject.getFields("data", "metadata")
//      fields match {
//        case data :: metadata :: Nil =>
//          val persistentClient = data.convertTo[PersistentClient]
//          val cqrsMetadata     = metadata.convertTo[CqrsMetadata]
//          println(persistentClient)
//          println(cqrsMetadata)
//
//          expectedData shouldBe persistentClient
//          assert(cqrsMetadata.sourceEvent.persistenceId.nonEmpty)
//          assert(cqrsMetadata.sourceEvent.sequenceNr >= 0)
//          assert(cqrsMetadata.sourceEvent.timestamp > 0)
//        case _                       => fail(s"Unexpected number of fields ${fields.size}. Content: $fields")
//      }
//      println("-------------------------------------------------------")
    }
  }
}
