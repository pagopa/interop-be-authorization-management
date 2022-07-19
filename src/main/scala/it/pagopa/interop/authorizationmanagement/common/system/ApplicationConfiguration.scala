package it.pagopa.interop.authorizationmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {

  val config: Config = ConfigFactory.load()

  val serverPort: Int = config.getInt("key-management.port")

  val jwtAudience: Set[String] = config.getString("key-management.jwt.audience").split(",").toSet.filter(_.nonEmpty)

  val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")

  val projectionsEnabled: Boolean = config.getBoolean("akka.projection.enabled")

  def projectionTag(index: Int) = s"interop-be-authorization-management-persistence|$index"

  require(jwtAudience.nonEmpty, "Audience cannot be empty")

}
