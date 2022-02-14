package it.pagopa.pdnd.interop.uservice.keymanagement.api.impl

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits._
import com.typesafe.scalalogging.Logger
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import it.pagopa.pdnd.interop.uservice.keymanagement.api.PurposeApiService
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system._
import it.pagopa.pdnd.interop.uservice.keymanagement.errors.KeyManagementErrors.{
  ClientEServiceStateUpdateError,
  ClientNotFoundError,
  ClientPurposeAdditionError
}
import it.pagopa.pdnd.interop.uservice.keymanagement.model._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence._
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.client.PersistentClientPurpose
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.impl.Validation
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends PurposeApiService
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
        statusReply.getError match {
          case err: ClientNotFoundError =>
            logger.info("Client {} not found on Purpose add", clientId, err)
            val problem = problemOf(StatusCodes.NotFound, err)
            addClientPurpose404(problem)
          case err =>
            logger.error("Error adding Purpose for Client {}", clientId, err)
            val problem =
              problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, seed.purposeId.toString))
            complete(problem.status, problem)
        }
      case Failure(ex) =>
        logger.error("Error adding Purpose for Client {}", clientId, ex)
        val problem =
          problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, seed.purposeId.toString))
        complete(problem.status, problem)
    }
  }

  override def updateEServiceState(eServiceId: String, seed: ClientEServiceDetailsSeed)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Updating EService {} state for all clients", eServiceId)

    val commanders: Seq[EntityRef[Command]] =
      (0 until settings.numberOfShards).map(shard =>
        sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
      )

    val result = for {
      shardResults <- commanders.traverse(_.ask(ref => UpdateEServiceState(eServiceId, seed, ref)))
      summaryResult = shardResults.collect {
        case shardResult if shardResult.isSuccess => Right(shardResult.getValue)
        case shardResult if shardResult.isError   => Left(shardResult.getError)
      }
      clientsUpdated = summaryResult.collect { case Right(n) => n }.sum
      failures       = summaryResult.collect { case Left(_) => 1 }.sum
      _              = logger.info("Clients updated for EService {}: {}. Failures: {}", eServiceId, clientsUpdated, failures)
      r <- summaryResult.sequence.toFuture
    } yield r

    onComplete(result) {
      case Success(_) =>
        updateEServiceState204
      case Failure(ex) =>
        logger.error("Error updating EService {} state for all clients", eServiceId, ex)
        val problem = problemOf(StatusCodes.InternalServerError, ClientEServiceStateUpdateError(eServiceId))
        complete(problem.status, problem)
    }

  }

}
