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
import scala.jdk.CollectionConverters._

object ClientCqrsProjection {
  def projection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig, projectionId: String)(
    implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): CqrsProjection[Event] =
    CqrsProjection[Event](offsetDbConfig, mongoDbConfig, projectionId, eventHandler)

  private def eventHandler(collection: MongoCollection[Document], event: Event): PartialMongoAction = event match {
    case ClientAdded(c)                  =>
      ActionWithDocument(collection.insertOne, Document(s"{ data: ${c.toJson.compactPrint} }"))
    case ClientDeleted(cId)              => Action(collection.deleteOne(Filters.eq("data.id", cId)))
    case KeysAdded(cId, keys)            =>
      val updates = keys.map { case (_, key) => Updates.push(s"data.keys", key.toDocument) }
      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.combine(updates.toList: _*))
    case KeyDeleted(cId, kId, _)         =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", cId), _),
        Updates.pull("data.keys", Document(s"{ kid : \"$kId\" }"))
      )
    case RelationshipAdded(c, rId)       =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", c.id.toString), _),
        Updates.push("data.relationships", rId.toString)
      )
    case RelationshipRemoved(cId, rId)   =>
      ActionWithBson(collection.updateOne(Filters.eq("data.id", cId), _), Updates.pull("data.relationships", rId))
    case ClientPurposeAdded(cId, states) =>
      // Added as array instead of map because it is not possible to update objects without knowing their key
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", cId), _),
        Updates.push(s"data.purposes", states.toDocument)
      )
    case ClientPurposeRemoved(cId, pId)  =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", cId), _),
        Updates.pull("data.purposes", Filters.eq("purpose.purposeId", pId))
      )
    case EServiceStateUpdated(eServiceId, descriptorId, state, audience, voucherLifespan) =>
      // Updates all purposes states of all clients matching criteria
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(List(Filters.and(Filters.eq("elem.eService.eServiceId", eServiceId))).asJava)
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].eService.state", state.toString),
          Updates.set("data.purposes.$[elem].eService.descriptorId", descriptorId.toString),
          Updates.set("data.purposes.$[elem].eService.audience", audience),
          Updates.set("data.purposes.$[elem].eService.voucherLifespan", voucherLifespan)
        )
      )
    case AgreementStateUpdated(eServiceId, consumerId, agreementId, state)                =>
      // Updates all purposes states of all clients matching criteria
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(
            List(
              Filters.and(
                Filters.eq("elem.agreement.eServiceId", eServiceId),
                Filters.eq("elem.agreement.consumerId", consumerId)
              )
            ).asJava
          )
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].agreement.state", state.toString),
          Updates.set("data.purposes.$[elem].agreement.agreementId", agreementId.toString)
        )
      )
    case PurposeStateUpdated(purposeId, versionId, state)                                 =>
      // Updates all purposes states of all clients matching criteria
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(List(Filters.eq("elem.purpose.purposeId", purposeId)).asJava)
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].purpose.state", state.toString),
          Updates.set("data.purposes.$[elem].purpose.versionId", versionId.toString)
        )
      )
  }

}
