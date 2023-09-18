package it.pagopa.interop.authorizationmanagement.model.persistence.projection

import akka.actor.typed.ActorSystem
import cats.implicits.toTraverseOps
import it.pagopa.interop.authorizationmanagement.api.impl.jwkKeyFormat
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.KeyAdapters._
import it.pagopa.interop.authorizationmanagement.jwk.converter.KeyConverter
import it.pagopa.interop.commons.cqrs.model._
import it.pagopa.interop.commons.cqrs.service.CqrsProjection
import org.mongodb.scala.model._
import org.mongodb.scala.{MongoCollection, _}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import spray.json.DefaultJsonProtocol.StringJsonFormat
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
    case KeysAdded(clientId, keys) =>
      val clientIdField: Map[String, JsValue]                 = Map("clientId" -> clientId.toJson)
      val updates: Either[Throwable, Seq[ActionWithDocument]] = keys.values.toSeq.traverse(key =>
        KeyConverter
          .fromBase64encodedPEMToAPIKey(key.kid, key.encodedPem, key.use.toJwk, key.algorithm)
          .map { jwk =>
            val data: JsObject = JsObject(jwk.toApi.toJson.asJsObject.fields ++ clientIdField)
            ActionWithDocument(collection.insertOne, Document(s"{ data: ${data.compactPrint} }"))
          }
      )
      updates.fold(ErrorAction, MultiAction)
    case KeyDeleted(_, kid, _)     =>
      Action(collection.deleteOne(Filters.eq("data.kid", kid)))
    case ClientDeleted(cId)        => Action(collection.deleteMany(Filters.eq("data.clientId", cId)))
    case _                         => NoOpAction
  }
}
