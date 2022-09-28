package it.pagopa.interop.authorizationmanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import it.pagopa.interop.authorizationmanagement.model.client.PersistentClient
import it.pagopa.interop.authorizationmanagement.model.persistence.JsonFormats._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import it.pagopa.interop.commons.cqrs.service.DocumentConversions._
import org.mongodb.scala.bson.BsonArray
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
    case e: ClientAdded                       =>
      ActionWithDocument(collection.insertOne, Document(s"{ data: ${e.client.toJson.compactPrint} }"))
    case e: ClientDeleted                     => Action(collection.deleteOne(Filters.eq("data.id", e.clientId)))
    case e: KeysAdded                         =>
      val updates = e.keys.map { case (_, key) => Updates.push(s"data.keys", key.toDocument) }
      ActionWithBson(collection.updateOne(Filters.eq("data.id", e.clientId), _), Updates.combine(updates.toList: _*))
    case e: KeyDeleted                        =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", e.clientId), _),
        Updates.pull("data.keys", Document(s"{ kid : \"${e.keyId}\" }"))
      )
    case e: RelationshipAdded                 =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", e.client.id.toString), _),
        Updates.push("data.relationships", e.relationshipId.toString)
      )
    case e: RelationshipRemoved               =>
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", e.clientId), _),
        Updates.pull("data.relationships", e.relationshipId)
      )
    case e: ClientPurposeAdded                =>
      // Added as array instead of map because it is not possible to update objects without knowing their key
      ActionWithBson(
        collection.updateOne(Filters.eq("data.id", e.clientId), _),
        Updates.push(s"data.purposes", e.statesChain.toDocument)
      )
    case e: ClientPurposeRemoved              =>
      // Note: Due to DocumentDB limitations, it is not possible, in a single instruction, to pull
      //   data from an array of objects filtering by a nested field of the object.
      //   e.g: { bar: [ { id: 1, v: { vv: "foo" } } ] }
      //   It is not possible to remove the element with id = 1 filtering by v.vv = "foo"

      val command = for {
        document <- collection.find(Filters.eq("data.id", e.clientId))
        client          = Utils.extractData[PersistentClient](document)
        updatedPurposes = client.purposes.filter(_.purpose.purposeId.toString != e.purposeId)
      } yield Updates.set("data.purposes", BsonArray.fromIterable(updatedPurposes.map(_.toDocument.toBsonDocument())))

      ActionWithObservable(collection.updateOne(Filters.eq("data.id", e.clientId), _), command)
    case e: EServiceStateUpdated              =>
      // Updates all purposes states of all clients matching criteria
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(List(Filters.eq("elem.eService.eServiceId", e.eServiceId)).asJava)
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].eService.state", e.state.toString),
          Updates.set("data.purposes.$[elem].eService.descriptorId", e.descriptorId.toString),
          Updates.set("data.purposes.$[elem].eService.audience", e.audience),
          Updates.set("data.purposes.$[elem].eService.voucherLifespan", e.voucherLifespan)
        )
      )
    case e: AgreementStateUpdated             =>
      // Updates all purposes states of all clients matching criteria
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(
            List(
              Filters.and(
                Filters.eq("elem.agreement.eServiceId", e.eServiceId),
                Filters.eq("elem.agreement.consumerId", e.consumerId)
              )
            ).asJava
          )
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].agreement.state", e.state.toString),
          Updates.set("data.purposes.$[elem].agreement.agreementId", e.agreementId.toString)
        )
      )
    case e: AgreementAndEServiceStatesUpdated =>
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(
            List(
              Filters.and(
                Filters.eq("elem.agreement.eServiceId", e.eServiceId),
                Filters.eq("elem.agreement.consumerId", e.consumerId)
              )
            ).asJava
          )
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].agreement.state", e.agreementState.toString),
          Updates.set("data.purposes.$[elem].agreement.agreementId", e.agreementId.toString),
          Updates.set("data.purposes.$[elem].eService.descriptorId", e.descriptorId.toString),
          Updates.set("data.purposes.$[elem].eService.state", e.eserviceState.toString)
        )
      )
    case e: PurposeStateUpdated               =>
      // Updates all purposes states of all clients matching criteria
      ActionWithBson(
        collection.updateMany(
          Filters.empty(),
          _,
          UpdateOptions().arrayFilters(List(Filters.eq("elem.purpose.purposeId", e.purposeId)).asJava)
        ),
        Updates.combine(
          Updates.set("data.purposes.$[elem].purpose.state", e.state.toString),
          Updates.set("data.purposes.$[elem].purpose.versionId", e.versionId.toString)
        )
      )
  }

}
