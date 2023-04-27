package it.pagopa.interop.authorizationmanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import cats.implicits.toTraverseOps
import it.pagopa.interop.authorizationmanagement.api.impl.keyFormat
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.service.impl.KeyProcessor
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoCollection, _}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import spray.json._

import scala.concurrent.ExecutionContext

object KeyCqrsProjection {

  def projection(offsetDbConfig: DatabaseConfig[JdbcProfile], mongoDbConfig: MongoDbConfig, projectionId: String)(
    implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): CqrsProjection[Event] =
    CqrsProjection[Event](offsetDbConfig, mongoDbConfig, projectionId, eventHandler)

  private def eventHandler(collection: MongoCollection[Document], event: Event): PartialMongoAction = event match {
    case KeysAdded(_, keys)    =>
      val updates: Either[Throwable, Seq[ActionWithDocument]] = keys.values.toSeq.traverse(key =>
        KeyProcessor
          .fromBase64encodedPEMToAPIKey(key.kid, key.encodedPem, key.use, key.algorithm)
          .map(jwk =>
            ActionWithDocument(collection.insertOne, Document(s"{ data: ${jwk.toJson.asJsObject.compactPrint}}"))
          )
      )
      updates.fold(ErrorAction, MultiAction)
    case KeyDeleted(_, kid, _) =>
      Action(collection.deleteOne(Filters.eq("data.kid", kid), _))
    case _                     => NoOpAction
  }
}
