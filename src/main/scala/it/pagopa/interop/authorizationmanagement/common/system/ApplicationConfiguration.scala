package it.pagopa.interop.authorizationmanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("key-management.port")

  lazy val jwtAudience: Set[String] = config.getStringList("key-management.jwt.audience").asScala.toSet

  lazy val numberOfProjectionTags: Int = config.getInt("akka.cluster.sharding.number-of-shards")

  def projectionTag(index: Int) = s"interop-be-authorization-management-persistence|$index"

}
