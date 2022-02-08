package it.pagopa.pdnd.interop.uservice.keymanagement.common.system

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters.ListHasAsScala

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  lazy val serverPort: Int = config.getInt("uservice-key-management.port")

  lazy val jwtAudience: Set[String] = config.getStringList("uservice-key-management.jwt.audience").asScala.toSet

}
