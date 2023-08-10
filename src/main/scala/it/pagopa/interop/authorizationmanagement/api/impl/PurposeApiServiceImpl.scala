package it.pagopa.interop.authorizationmanagement.api.impl

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import akka.cluster.sharding.typed.{ClusterShardingSettings, ShardingEnvelope}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.pattern.StatusReply
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.authorizationmanagement.api.PurposeApiService
import it.pagopa.interop.authorizationmanagement.api.impl.PurposeApiResponseHandlers._
import it.pagopa.interop.authorizationmanagement.common.system._
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.client.{PersistentClientComponentState, PersistentClientPurpose}
import it.pagopa.interop.authorizationmanagement.model.persistence.ClientAdapters._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.impl.Validation
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.service.UUIDSupplier

import scala.concurrent.{ExecutionContext, Future}

final case class PurposeApiServiceImpl(
  system: ActorSystem[_],
  sharding: ClusterSharding,
  entity: Entity[Command, ShardingEnvelope[Command]],
  uuidSupplier: UUIDSupplier
)(implicit ec: ExecutionContext)
    extends PurposeApiService
    with Validation {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private implicit val settings: ClusterShardingSettings = shardingSettings(entity, system)
  private implicit val implicitSharding: ClusterSharding = sharding

  override def addClientPurpose(clientId: String, seed: PurposeSeed)(implicit
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Adding Purpose for Client $clientId"
    logger.info(operationLabel)

    val persistentClientPurpose = PersistentClientPurpose.fromSeed(uuidSupplier)(seed)
    val result: Future[Purpose] =
      commander(clientId).askWithStatus(ref => AddClientPurpose(clientId, persistentClientPurpose, ref)).map(_.toApi)

    onComplete(result) { addClientPurposeResponse[Purpose](operationLabel)(addClientPurpose200) }
  }

  override def removeClientPurpose(clientId: String, purposeId: String)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Removing Purpose $purposeId from Client $clientId"
    logger.info(operationLabel)

    val result: Future[Unit] = commander(clientId).askWithStatus(ref => RemoveClientPurpose(clientId, purposeId, ref))

    onComplete(result) { removeClientPurposeResponse[Unit](operationLabel)(_ => removeClientPurpose204) }
  }

  override def updateEServiceState(eServiceId: String, payload: ClientEServiceDetailsUpdate)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Updating EService $eServiceId state for all clients"
    logger.info(operationLabel)

    val result: Future[Seq[Unit]] = updateStateOnClients(
      UpdateEServiceState(
        eServiceId,
        payload.descriptorId,
        PersistentClientComponentState.fromApi(payload.state),
        payload.audience,
        payload.voucherLifespan,
        _
      )
    )

    onComplete(result) { updateEServiceStateResponse[Seq[Unit]](operationLabel)(_ => updateEServiceState204) }

  }

  override def updateAgreementState(
    eServiceId: String,
    consumerId: String,
    payload: ClientAgreementDetailsUpdate
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    val operationLabel: String =
      s"Updating Agreement with EService $eServiceId and Consumer $consumerId state for all clients"
    logger.info(operationLabel)

    val result: Future[Seq[Unit]] = updateStateOnClients(
      UpdateAgreementState(
        eServiceId,
        consumerId,
        payload.agreementId,
        PersistentClientComponentState.fromApi(payload.state),
        _
      )
    )

    onComplete(result) { updateAgreementStateResponse[Seq[Unit]](operationLabel)(_ => updateAgreementState204) }

  }

  override def updateAgreementAndEServiceStates(
    eServiceId: String,
    consumerId: String,
    payload: ClientAgreementAndEServiceDetailsUpdate
  )(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route = {
    val operationLabel: String =
      s"Updating Agreement and EService with EService $eServiceId and Consumer $consumerId state for all clients"
    logger.info(operationLabel)

    val result: Future[Seq[Unit]] = updateStateOnClients(
      UpdateAgreementAndEServiceState(
        eServiceId = eServiceId,
        descriptorId = payload.descriptorId,
        consumerId = consumerId,
        agreementId = payload.agreementId,
        agreementState = PersistentClientComponentState.fromApi(payload.agreementState),
        eServiceState = PersistentClientComponentState.fromApi(payload.eserviceState),
        audience = payload.audience,
        voucherLifespan = payload.voucherLifespan,
        _
      )
    )

    onComplete(result) {
      updateAgreementAndEServiceStatesResponse[Seq[Unit]](operationLabel)(_ => updateAgreementAndEServiceStates204)
    }

  }

  override def updatePurposeState(purposeId: String, payload: ClientPurposeDetailsUpdate)(implicit
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    contexts: Seq[(String, String)]
  ): Route = {
    val operationLabel: String = s"Updating Purpose $purposeId state for all clients"
    logger.info(operationLabel)

    val result: Future[Seq[Unit]] =
      updateStateOnClients(
        UpdatePurposeState(purposeId, payload.versionId, PersistentClientComponentState.fromApi(payload.state), _)
      )

    onComplete(result) { updatePurposeStateResponse[Seq[Unit]](operationLabel)(_ => updatePurposeState204) }

  }

  private def updateStateOnClients(event: ActorRef[StatusReply[Unit]] => Command): Future[Seq[Unit]] = {
    val commanders: Seq[EntityRef[Command]] =
      (0 until settings.numberOfShards).map(shard =>
        sharding.entityRefFor(KeyPersistentBehavior.TypeKey, shard.toString)
      )

    for {
      shardResults  <- Future.traverse(commanders)(_.ask[StatusReply[Unit]](event))
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
