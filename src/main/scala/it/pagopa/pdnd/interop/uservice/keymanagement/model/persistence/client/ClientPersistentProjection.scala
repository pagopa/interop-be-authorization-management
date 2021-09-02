package it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{AtLeastOnceFlowProjection, SourceProvider}
import akka.projection.{ProjectionContext, ProjectionId}
import akka.stream.scaladsl.FlowWithContext
import akka.{Done, NotUsed}

import scala.concurrent.duration.DurationInt

@SuppressWarnings(Array("org.wartremover.warts.StringPlusAny"))
class ClientPersistentProjection(system: ActorSystem[_], entity: Entity[ClientCommand, ShardingEnvelope[ClientCommand]]) {

  private val settings: ClusterShardingSettings = entity.settings match {
    case None    => ClusterShardingSettings(system)
    case Some(s) => s
  }

  def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[ClientEvent]] =
    EventSourcedProvider
      .eventsByTag[ClientEvent](system, readJournalPluginId = CassandraReadJournal.Identifier, tag = tag)

  val flow
    : FlowWithContext[EventEnvelope[ClientEvent], ProjectionContext, EventEnvelope[ClientEvent], ProjectionContext, NotUsed]#Repr[
    ClientEvent,
      ProjectionContext
    ]#Repr[Done.type, ProjectionContext] = FlowWithContext[EventEnvelope[ClientEvent], ProjectionContext]
    .map(envelope => envelope.event)
    .map(event => {
      println(event)
      Done
    })

  def projection(tag: String): AtLeastOnceFlowProjection[Offset, EventEnvelope[ClientEvent]] = {
    CassandraProjection
      .atLeastOnceFlow(projectionId = ProjectionId("clients-projections", tag), sourceProvider(tag), handler = flow)
      .withRestartBackoff(minBackoff = 10.seconds, maxBackoff = 60.seconds, randomFactor = 0.5)
  }

  val projections: Seq[AtLeastOnceFlowProjection[Offset, EventEnvelope[ClientEvent]]] =
    (0 until settings.numberOfShards).map(i =>
      projection(s"pdnd-interop-uservice-pdnd-uservice-key-management-client-persistence|$i")
    )

}
