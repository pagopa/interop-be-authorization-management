package it.pagopa.pdnd.interop.uservice.keymanagement.common

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.http.scaladsl.server.Directives.Authenticator
import akka.http.scaladsl.server.directives.Credentials
import akka.util.Timeout

import scala.concurrent.duration.DurationInt

package object system {

  implicit val timeout: Timeout = 300.seconds

  object Authenticator extends Authenticator[Unit] {
    override def apply(credentials: Credentials): Option[Unit] = Some(())
  }

  def shardingSettings[T](
    persistentEntity: Entity[T, ShardingEnvelope[T]],
    actorSystem: ActorSystem[_]
  ): ClusterShardingSettings =
    persistentEntity.settings match {
      case None    => ClusterShardingSettings(actorSystem)
      case Some(s) => s
    }

  @inline def getShard(id: String, numberOfShards: Int): String = Math.abs(id.hashCode % numberOfShards).toString

}
