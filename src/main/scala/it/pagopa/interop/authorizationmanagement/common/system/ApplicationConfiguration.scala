package it.pagopa.interop.authorizationmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}
import it.pagopa.interop.commons.cqrs.model.MongoDbConfig

object ApplicationConfiguration {

  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("authorization-management.port")

  val jwtAudience: Set[String] =
    config.getString("authorization-management.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")

  val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

  def projectionTag(index: Int) = s"interop-be-authorization-management-persistence|$index"

  // Loaded only if projections are enabled
  lazy val clientsMongoDB: MongoDbConfig = {
    val connectionString: String = config.getString("cqrs-projection.clients.connection-string")
    val dbName: String           = config.getString("cqrs-projection.clients.name")
    val collectionName: String   = config.getString("cqrs-projection.clients.collection-name")

    MongoDbConfig(connectionString, dbName, collectionName)
  }

  lazy val keysMongoDB: MongoDbConfig = {
    val connectionString: String = config.getString("cqrs-projection.keys.connection-string")
    val dbName: String           = config.getString("cqrs-projection.keys.name")
    val collectionName: String   = config.getString("cqrs-projection.keys.collection-name")

    MongoDbConfig(connectionString, dbName, collectionName)
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}
