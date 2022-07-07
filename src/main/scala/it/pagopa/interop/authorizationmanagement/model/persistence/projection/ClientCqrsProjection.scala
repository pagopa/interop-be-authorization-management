package it.pagopa.interop.authorizationmanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import it.pagopa.interop.commons.cqrs.service.DocumentConversions._
import org.mongodb.scala.{MongoCollection, _}
import org.mongodb.scala.model._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext

object ClientCqrsProjection {
  def projection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig)(implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): CqrsProjection[Event] =
    CqrsProjection[Event](offsetDbConfig, mongoDbConfig, projectionId = "client-cqrs-projections", eventHandler)

  private def eventHandler(collection: MongoCollection[Document], event: Event): PartialMongoAction = event match {
    case ClientAdded(c)       =>
      ActionWithDocument(collection.insertOne, Document(s"{ data: ${c.toJson.compactPrint} }"))
    case ClientDeleted(cId)   => Action(collection.deleteOne(Filters.eq("data.id", cId)))
    case KeysAdded(cId, keys) =>
      // TODO To be tested. Check if new keys are added and old keys are kept
      val updates = keys.map { case (kid, key) => Updates.set(s"data.keys.$kid", key.toDocument) }
      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.combine(updates.toList: _*))
  }

}
