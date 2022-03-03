package it.pagopa.interop.authorizationmanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits._
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.authorizationmanagement.api.PurposeApiService
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.client.{
  PersistentClientComponentState,
  PersistentClientPurpose
}
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.UUIDSupplier
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
            logger.info(s"Client $clientId not found on Purpose add - ${err.getMessage}")
            val problem = problemOf(StatusCodes.NotFound, err)
            addClientPurpose404(problem)
          case err =>
            logger.error(s"Error adding Purpose for Client ${clientId} - ${err.getMessage}")
            val problem =
              problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, seed.purposeId.toString))
            complete(problem.status, problem)
        }
      case Failure(ex) =>
        logger.error(s"Error adding Purpose for Client ${clientId} - ${ex.getMessage}")
        val problem =
          problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, seed.purposeId.toString))
        complete(problem.status, problem)
    }
  }

  override def removeClientPurpose(clientId: String, purposeId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Removing Purpose {} from Client {}", purposeId, clientId)

    val commander: EntityRef[Command] =
      sharding.entityRefFor(KeyPersistentBehavior.TypeKey, getShard(clientId, settings.numberOfShards))

    val result: Future[StatusReply[Unit]] = commander.ask(ref => RemoveClientPurpose(clientId, purposeId, ref))

    onComplete(result) {
      case Success(statusReply) if statusReply.isSuccess =>
        removeClientPurpose204
      case Success(statusReply) =>
        statusReply.getError match {
          case err: ClientNotFoundError =>
            logger.info(s"Client ${clientId} not found on Purpose ${purposeId} remove - ${err.getMessage}")
            val problem = problemOf(StatusCodes.NotFound, err)
            addClientPurpose404(problem)
          case err =>
            logger.error(s"Error removing Purpose ${purposeId} from Client ${clientId} - ${err.getMessage}")
            val problem =
              problemOf(StatusCodes.InternalServerError, ClientPurposeRemovalError(clientId, purposeId))
            complete(problem.status, problem)
        }
      case Failure(ex) =>
        logger.error(s"Error removing Purpose ${purposeId} from Client ${clientId} - ${ex.getMessage}")
        val problem =
          problemOf(StatusCodes.InternalServerError, ClientPurposeAdditionError(clientId, purposeId))
        complete(problem.status, problem)
    }
  }

  override def updateEServiceState(eServiceId: String, payload: ClientEServiceDetailsUpdate)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Updating EService {} state for all clients", eServiceId)

    val result: Future[Seq[Unit]] = updateStateOnClients(
      UpdateEServiceState(
        eServiceId,
        PersistentClientComponentState.fromApi(payload.state),
        payload.audience,
        payload.voucherLifespan,
        _
      )
    )

    onComplete(result) {
      case Success(_) =>
        updateEServiceState204
      case Failure(ex) =>
        logger.error(s"Error updating EService ${eServiceId} state for all clients - ${ex.getMessage}")
        val problem = problemOf(StatusCodes.InternalServerError, ClientEServiceStateUpdateError(eServiceId))
        complete(problem.status, problem)
    }

  }

  override def updateAgreementState(agreementId: String, payload: ClientAgreementDetailsUpdate)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Updating Agreement {} state for all clients", agreementId)

    val result: Future[Seq[Unit]] = updateStateOnClients(
      UpdateAgreementState(agreementId, PersistentClientComponentState.fromApi(payload.state), _)
    )

    onComplete(result) {
      case Success(_) =>
        updateAgreementState204
      case Failure(ex) =>
        logger.error(s"Error updating Agreement ${agreementId} state for all clients - ${ex.getMessage}")
        val problem = problemOf(StatusCodes.InternalServerError, ClientAgreementStateUpdateError(agreementId))
        complete(problem.status, problem)
    }

  }

  override def updatePurposeState(purposeId: String, payload: ClientPurposeDetailsUpdate)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    logger.info("Updating Purpose {} state for all clients", purposeId)

    val result: Future[Seq[Unit]] = updateStateOnClients(
      UpdatePurposeState(purposeId, PersistentClientComponentState.fromApi(payload.state), _)
    )

    onComplete(result) {
      case Success(_) =>
        updatePurposeState204
      case Failure(ex) =>
        logger.error(s"Error updating Purpose ${purposeId} state for all clients - ${ex.getMessage}")
        val problem = problemOf(StatusCodes.InternalServerError, ClientPurposeStateUpdateError(purposeId))
        complete(problem.status, problem)
    }

  }

  private def updateStateOnClients(event: ActorRef[StatusReply[Unit]] => Command): Future[Seq[Unit]] = {
    val commanders: Seq[EntityRef[Command]] =
      (0 until settings.numberOfShards).map(shard =>
        sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
      )

    for {
      shardResults <- commanders.traverse(_.ask[StatusReply[Unit]](event))
      summaryResult <- shardResults
        .collect {
          case shardResult if shardResult.isSuccess => Right(shardResult.getValue)
          case shardResult if shardResult.isError   => Left(shardResult.getError)
        }
        .sequence
        .toFuture
    } yield summaryResult
  }

}
