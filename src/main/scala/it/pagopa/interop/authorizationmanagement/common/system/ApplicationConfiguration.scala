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

  lazy val queueUrl: String = config.getString("authorization-management.persistence-events-queue-url")

  // Loaded only if projections are enabled
  lazy val (clientsMongoDB, keysMongoDB): (MongoDbConfig, MongoDbConfig) = {
    val connectionString: String = config.getString("cqrs-projection.db.connection-string")
    val dbName: String           = config.getString("cqrs-projection.db.name")

    (
      MongoDbConfig(connectionString, dbName, config.getString("cqrs-projection.db.clients-collection-name")),
      MongoDbConfig(connectionString, dbName, config.getString("cqrs-projection.db.keys-collection-name"))
    )
  }

  require(jwtAudience.nonEmpty, "Audience cannot be empty")
}
