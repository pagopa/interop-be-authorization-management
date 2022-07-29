package it.pagopa.interop.authorizationmanagement.projection.cqrs

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.dimafeng.testcontainers.DockerComposeContainer
import com.dimafeng.testcontainers.DockerComposeContainer.ComposeFile
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import it.pagopa.interop.authorizationmanagement.common.system.ApplicationConfiguration
import it.pagopa.interop.authorizationmanagement.{ItSpecConfiguration, ItSpecHelper}
import it.pagopa.interop.commons.cqrs.model.MongoDbConfig
import org.mongodb.scala.{ConnectionString, MongoClient, MongoClientSettings}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.connection.NettyStreamFactoryFactory
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.File
import java.util.UUID

// Testing only the projection is not possible (https://github.com/akka/akka-projection/issues/454)

class CqrsProjectionSpec
    extends ScalaTestWithActorTestKit(ItSpecConfiguration.config)
    with ItSpecHelper
    with AnyWordSpecLike
    with TestContainersForAll {

//  override def beforeAll(): Unit = {
//    startServer()
//  }
//
//  override def afterAll(): Unit = {
//    shutdownServer()
//    super.afterAll()
//  }

  val mongoDbConfig: MongoDbConfig = ApplicationConfiguration.mongoDb

  var mongodbClient: MongoClient = MongoClient(
    MongoClientSettings
      .builder()
      .applyConnectionString(new ConnectionString(mongoDbConfig.connectionString))
      .codecRegistry(DEFAULT_CODEC_REGISTRY)
      .streamFactoryFactory(NettyStreamFactoryFactory())
      .build()
  )

  override type Containers = DockerComposeContainer

  override def startContainers(): Containers =
    DockerComposeContainer.Def(ComposeFile(Left(new File("src/it/resources/docker-compose-it.yaml")))).start()

  override def afterContainersStart(containers: Containers): Unit = {
    super.afterContainersStart(containers)
//
//    containers match {
//      case dcContainer =>
//        System.setProperty("IT_POSTGRES_JDBC_STRING", postgreSQLContainer.jdbcUrl)
//        System.setProperty("IT_MONGODB_CONNECTION_STRING", mongodbContainer.replicaSetUrl)
//
//        println("-------------------------------------------------------")
//        println(postgreSQLContainer.jdbcUrl)
//        println(mongodbContainer.replicaSetUrl)
//        println(mongodbContainer.container.getConnectionString)
//        println("-------------------------------------------------------")
//
//    }

//    Thread.sleep(600000)
//    // TODO Improve service start and add graceful stop
//    Main.main(Array.empty)
    Thread.sleep(10000)
    startServer()
  }

  override def beforeContainersStop(containers: Containers): Unit = {
    shutdownServer()
//    Thread.sleep(30000)
    super.afterContainersStart(containers)
  }

  "request" should {
    "succeed" in {
//      val response = request(s"$serviceURL/build-info", HttpMethods.GET, None)

      val client = createClient(UUID.randomUUID(), UUID.randomUUID())
      println("-------------------------------------------------------")
      println(client)
      println(mongodbClient.listDatabaseNames().toFuture().futureValue)
//      Thread.sleep(30000)
      println("-------------------------------------------------------")

      //    withContainers { case postgreSQLContainer and mongodbContainer =>
      ////      println("-------------------------------------------------------")
      ////      println(postgreSQLContainer.jdbcUrl)
      ////      println(mongodbContainer.replicaSetUrl)
      ////      println(mongodbContainer.container.getConnectionString)
      ////      println("-------------------------------------------------------")
      //      // Inside your test body you can do with your containers whatever you want to
      //      assert(postgreSQLContainer.jdbcUrl.nonEmpty && mongodbContainer.replicaSetUrl.nonEmpty)
      //    }
    }
  }
}
