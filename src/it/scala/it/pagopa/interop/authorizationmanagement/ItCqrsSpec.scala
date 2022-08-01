package it.pagopa.interop.authorizationmanagement

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.DockerComposeContainer.ComposeFile
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.commons.cqrs.model.{CqrsMetadata, MongoDbConfig}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ConnectionString, Document, MongoClient, MongoClientSettings}
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._

import java.io.File
import scala.concurrent.Future

// Testing only the projection is not possible (https://github.com/akka/akka-projection/issues/454)

class ItCqrsSpec
    extends ScalaTestWithActorTestKit(ItSpecConfiguration.config)
    with ItSpecHelper
    with AnyWordSpecLike
    with TestContainersForAll {

  private var internalMongodbClient: Option[MongoClient] = None
  private val mongoDbConfig: MongoDbConfig               = ApplicationConfiguration.mongoDb

//  implicit val mongodbOpTimeout: FiniteDuration = 5.seconds
//  val mongodbOpWaitTime: FiniteDuration         = 100.millis

  override type Containers = DockerComposeContainer

  override def startContainers(): Containers =
    DockerComposeContainer.Def(ComposeFile(Left(new File("src/it/resources/docker-compose-it.yaml")))).start()

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)

    // TODO Implement better readiness wait
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

  //  def find[T](id: String)(implicit timeout: FiniteDuration, jsonReader: JsonReader[T]): Future[T] = for {
  //    results <- mongodbClient
  //      .getDatabase(mongoDbConfig.dbName)
  //      .getCollection(mongoDbConfig.collectionName)
  //      .find(Filters.eq("data.id", id))
  //      .toFuture()
  //    value   <-
  //      if (timeout - mongodbOpWaitTime <= 0.seconds)
  //        fail(s"Timeout on mongodb find for id $id")
  //      else if (results.isEmpty) {
  //        Future.successful(Thread.sleep(mongodbOpWaitTime.toMillis))
  //        find[T](id)(timeout - mongodbOpWaitTime, jsonReader)
  //      } else Future.successful(extractData[T](results.head))
  //  } yield value

  def findOne[T: JsonReader](id: String): Future[T] = find[T](id).map(_.head)

  def find[T: JsonReader](id: String): Future[Seq[T]] = for {
    // Wait a reasonable amount of time to allow the event to be processed by the projection
    _       <- Future.successful(Thread.sleep(2500))
    results <- mongodbClient
      .getDatabase(mongoDbConfig.dbName)
      .getCollection(mongoDbConfig.collectionName)
      .find(Filters.eq("data.id", id))
      .toFuture()
  } yield results.map(extractData[T])

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
    super.afterContainersStart(containers)
  }

}
