package it.pagopa.interop.authorizationmanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import it.pagopa.interop.commons.cqrs.service.DocumentConversions._
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoCollection, _}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext

object ClientCqrsProjection {
  def projection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig, projectionId: String)(
    implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): CqrsProjection[Event] =
    CqrsProjection[Event](offsetDbConfig, mongoDbConfig, projectionId, eventHandler)

  private def eventHandler(collection: MongoCollection[Document], event: Event): PartialMongoAction = event match {
    case ClientAdded(c)                       =>
      val reformat: Document =
//        Document(s"{ data: ${c.toJson.compactPrint} }") ++          Document("{ data: { purposes: [] } }")
        Document(s"{ data: ${Document(c.toJson.compactPrint) ++ Document("{ purposes: [] }")} }")

      ActionWithDocument(collection.insertOne, reformat)
    case ClientDeleted(cId)                   => Action(collection.deleteOne(Filters.eq("data.id", cId)))
    case KeysAdded(cId, keys)                 =>
//      val updates = keys.map { case (kid, key) => Updates.set(s"data.keys.$kid", key.toDocument) }
//      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.combine(updates.toList: _*))
      val updates = keys.map { case (kid, key) => Updates.push(s"data.keys", MapEntryDocument(kid, key).toDocument) }
      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.combine(updates.toList: _*))
    case KeyDeleted(cId, kId, _)              =>
//      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.unset(s"data.keys.$kId"))
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", cId), _),
        Updates.pull("data.keys", Document(s"{ kid : \"$kId\" }"))
      )
    case RelationshipAdded(c, rId)            =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", c.id.toString), _),
        Updates.push("data.relationships", rId.toString)
      )
    case RelationshipRemoved(cId, rId)        =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.pull("data.relationships", rId))
    case ClientPurposeAdded(cId, pId, states) =>
      // Added as array instead of map because it is not possible to update objects without knowing their key
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", cId), _),
        Updates.push(s"data.purposes", MapEntryDocument(pId, states).toDocument)
      )
    case ClientPurposeRemoved(cId, pId)       =>
      // TODO test
      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.unset(s"data.keys.$pId"))
    case EServiceStateUpdated(eServiceId, descriptorId, state, audience, voucherLifespan) =>
      // TODO wrong
      ActionWithBson(
        collection.updateMany(
          Filters.and(
            Filters.eq("data.purposes.eService.eServiceId", eServiceId),
            Filters.eq("data.purposes.eService.descriptorId", descriptorId.toString)
          ),
          _
        ),
        Updates.combine(
          Updates.set(s"data.purposes.eService.state", state.toJson.compactPrint),
          Updates.set(s"data.purposes.eService.audience", audience),
          Updates.set(s"data.purposes.eService.voucherLifespan", voucherLifespan)
        )
      )
    // TODO delete me
    case _ => throw new Exception("Not yet implemented")
  }

}
