package it.pagopa.pdnd.interop.uservice.keymanagement.common

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt

package object system {

  implicit val timeout: Timeout = 300.seconds

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
