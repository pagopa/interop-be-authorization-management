package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.api.PurposeApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.KeyManagementErrors.{
  ClientNotFoundError,
  ClientPurposeAdditionError
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClientPurpose
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

final case class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
) extends PurposeApiService
    with Validation {

  private val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  private val settings: ClusterShardingSettings = shardingSettings(entity, system)

  override def addClientPurpose(clientId: String, seed: PurposeSeed)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Adding Purpose for Client {}", clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val persistentClientPurpose = PersistentClientPurpose.fromSeed(uuidSupplier)(seed)
    val result: Future[StatusReply[PersistentClientPurpose]] =
      commander.ask(ref => AddClientPurpose(clientId, persistentClientPurpose, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        addClientPurpose200(statusReply.getValue.toApi)
      case Success(statusReply) =>
        logger.error("Error adding Purpose for Client {} with seed {}", clientId, statusReply.getError, seed)
        val problem =
          problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, seed.purposeId.toString))
        complete(problem.status, problem)
      case Failure(ex: ClientNotFoundError) =>
        logger.info("Client {} not found on Purpose add", clientId, ex)
        val problem = problemOf(StatusCodes.NotFound, ex)
        addClientPurpose404(problem)
      case Failure(ex) =>
        logger.error("Error adding Purpose for Client {}", clientId, ex)
        val problem =
          problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, seed.purposeId.toString))
        complete(problem.status, problem)
    }
  }

}
