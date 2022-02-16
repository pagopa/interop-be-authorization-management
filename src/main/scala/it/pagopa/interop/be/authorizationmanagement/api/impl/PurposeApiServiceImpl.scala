package it.pagopa.interop.be.authorizationmanagement.api.impl

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
import it.pagopa.interop.be.authorizationmanagement.api.PurposeApiService
import it.pagopa.interop.be.authorizationmanagement.common.system._
import it.pagopa.interop.be.authorizationmanagement.errors.KeyManagementErrors._
import it.pagopa.interop.be.authorizationmanagement.model._
import it.pagopa.interop.be.authorizationmanagement.model.persistence._
import it.pagopa.interop.be.authorizationmanagement.model.persistence.client.{
  PersistentClientComponentState,
  PersistentClientPurpose
}
import it.pagopa.interop.be.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.getShard
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
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
        logger.error("Error updating EService {} state for all clients", eServiceId, ex)
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
        logger.error("Error updating Agreement {} state for all clients", agreementId, ex)
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
        logger.error("Error updating Purpose {} state for all clients", purposeId, ex)
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
