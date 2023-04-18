package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import cats.implicits._
import it.pagopa.interop.authorizationmanagement.model.client._
import it.pagopa.interop.authorizationmanagement.model.key.{Enc, PersistentKey, PersistentKeyUse, Sig}
import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes.Keys
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.client._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.events._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.key.{
  KeyUseV1,
  PersistentKeyEntryV1,
  PersistentKeyV1
}
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.state.{
  StateClientsEntryV1,
  StateKeysEntryV1,
  StateV1
}
import it.pagopa.interop.commons.utils.TypeConversions._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.util.Try

package object v1 {

  type ErrorOr[A] = Either[Throwable, A]

  final val DEFAULT_CREATED_AT: OffsetDateTime = OffsetDateTime.of(2023, 4, 18, 12, 0, 0, 0, ZoneOffset.UTC)

  implicit def stateV1PersistEventDeserializer: PersistEventDeserializer[StateV1, State] =
    state => {
      for {
        keys    <- state.keys
          .traverse[ErrorOr, (String, Keys)] { key =>
            protoEntryToKey(key.keyEntries)
              .map(entry => key.clientId -> entry.toMap)
          }
          .map(_.toMap)
        clients <- state.clients
          .traverse[ErrorOr, (String, PersistentClient)](protoEntryToClient)
          .map(_.toMap)
      } yield State(keys, clients)
    }

  implicit def stateV1PersistEventSerializer: PersistEventSerializer[State, StateV1] =
    state => {
      for {
        keys    <- state.keys.toSeq.traverse[ErrorOr, StateKeysEntryV1] { case (client, keys) =>
          keyToEntry(keys).map(entries => StateKeysEntryV1(client, entries))
        }
        clients <- state.clients.toSeq.traverse[ErrorOr, StateClientsEntryV1] { case (_, client) =>
          clientToStateEntry(client)
        }
      } yield StateV1(keys = keys, clients = clients)

    }

  implicit def keysAddedV1PersistEventDeserializer: PersistEventDeserializer[KeysAddedV1, KeysAdded] = event => {
    protoEntryToKey(event.keys).map(keys => KeysAdded(clientId = event.clientId, keys = keys.toMap))
  }

  implicit def keysAddedV1PersistEventSerializer: PersistEventSerializer[KeysAdded, KeysAddedV1] = event => {
    keyToEntry(event.keys).map(keys => KeysAddedV1(clientId = event.clientId, keys = keys))
  }

  implicit def keyDeletedV1PersistEventDeserializer: PersistEventDeserializer[KeyDeletedV1, KeyDeleted] = event =>
    event.deactivationTimestamp.toOffsetDateTime.toEither.map(deactivationTimestamp =>
      KeyDeleted(clientId = event.clientId, keyId = event.keyId, deactivationTimestamp = deactivationTimestamp)
    )

  implicit def keyDeletedV1PersistEventSerializer: PersistEventSerializer[KeyDeleted, KeyDeletedV1] = event =>
    event.deactivationTimestamp.asFormattedString.toEither.map(deactivationTimestamp =>
      KeyDeletedV1(clientId = event.clientId, keyId = event.keyId, deactivationTimestamp = deactivationTimestamp)
    )

  implicit def clientAddedV1PersistEventDeserializer: PersistEventDeserializer[ClientAddedV1, ClientAdded] = event =>
    protobufToClient(event.client).map(ClientAdded)

  implicit def clientAddedV1PersistEventSerializer: PersistEventSerializer[ClientAdded, ClientAddedV1] = event =>
    clientToProtobuf(event.client).map(client => ClientAddedV1(client = client))

  implicit def clientDeletedV1PersistEventDeserializer: PersistEventDeserializer[ClientDeletedV1, ClientDeleted] =
    event => Right[Throwable, ClientDeleted](ClientDeleted(clientId = event.clientId))

  implicit def clientDeletedV1PersistEventSerializer: PersistEventSerializer[ClientDeleted, ClientDeletedV1] = event =>
    Right[Throwable, ClientDeletedV1](ClientDeletedV1(clientId = event.clientId))

  implicit def relationshipAddedV1PersistEventDeserializer
    : PersistEventDeserializer[RelationshipAddedV1, RelationshipAdded] = event =>
    for {
      client         <- protobufToClient(event.client)
      relationshipId <- Try(UUID.fromString(event.relationshipId)).toEither
    } yield RelationshipAdded(client = client, relationshipId = relationshipId)

  implicit def relationshipAddedV1PersistEventSerializer
    : PersistEventSerializer[RelationshipAdded, RelationshipAddedV1] = event =>
    for {
      client <- clientToProtobuf(event.client)
    } yield RelationshipAddedV1(client = client, relationshipId = event.relationshipId.toString)

  implicit def relationshipRemovedV1PersistEventDeserializer
    : PersistEventDeserializer[RelationshipRemovedV1, RelationshipRemoved] =
    event =>
      Right[Throwable, RelationshipRemoved](
        RelationshipRemoved(clientId = event.clientId, relationshipId = event.relationshipId)
      )

  implicit def relationshipRemovedV1PersistEventSerializer
    : PersistEventSerializer[RelationshipRemoved, RelationshipRemovedV1] = event =>
    Right[Throwable, RelationshipRemovedV1](
      RelationshipRemovedV1(clientId = event.clientId, relationshipId = event.relationshipId)
    )

  implicit def clientPurposeAddedV1PersistEventDeserializer
    : PersistEventDeserializer[ClientPurposeAddedV1, ClientPurposeAdded] =
    event =>
      protobufToClientStatesChain(event.statesChain)
        .map(statesChain => ClientPurposeAdded(clientId = event.clientId, statesChain = statesChain))

  implicit def clientPurposeAddedV1PersistEventSerializer
    : PersistEventSerializer[ClientPurposeAdded, ClientPurposeAddedV1] = event =>
    Right[Throwable, ClientPurposeAddedV1](
      ClientPurposeAddedV1(clientId = event.clientId, statesChain = clientStatesChainToProtobuf(event.statesChain))
    )

  implicit def clientPurposeRemovedV1PersistEventDeserializer
    : PersistEventDeserializer[ClientPurposeRemovedV1, ClientPurposeRemoved] =
    event => Right(ClientPurposeRemoved(clientId = event.clientId, purposeId = event.purposeId))

  implicit def clientPurposeRemovedV1PersistEventSerializer
    : PersistEventSerializer[ClientPurposeRemoved, ClientPurposeRemovedV1] = event =>
    Right(ClientPurposeRemovedV1(clientId = event.clientId, purposeId = event.purposeId))

  implicit def eServiceStateUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[EServiceStateUpdatedV1, EServiceStateUpdated] =
    event =>
      for {
        descriptorId <- event.descriptorId.toUUID.toEither
        state        <- protobufToComponentState(event.state)
      } yield EServiceStateUpdated(
        eServiceId = event.eServiceId,
        descriptorId = descriptorId,
        state = state,
        audience = event.audience,
        voucherLifespan = event.voucherLifespan
      )

  implicit def eServiceStateUpdatedV1PersistEventSerializer
    : PersistEventSerializer[EServiceStateUpdated, EServiceStateUpdatedV1] = event =>
    Right[Throwable, EServiceStateUpdatedV1](
      EServiceStateUpdatedV1.of(
        eServiceId = event.eServiceId,
        descriptorId = event.descriptorId.toString,
        state = componentStateToProtobuf(event.state),
        audience = event.audience,
        voucherLifespan = event.voucherLifespan
      )
    )

  implicit def agreementStateUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementStateUpdatedV1, AgreementStateUpdated] =
    event =>
      for {
        agreementId <- event.agreementId.toUUID.toEither
        state       <- protobufToComponentState(event.state)
      } yield AgreementStateUpdated(
        eServiceId = event.eServiceId,
        consumerId = event.consumerId,
        agreementId = agreementId,
        state = state
      )

  implicit def agreementStateUpdatedV1PersistEventSerializer
    : PersistEventSerializer[AgreementStateUpdated, AgreementStateUpdatedV1] = event =>
    Right[Throwable, AgreementStateUpdatedV1](
      AgreementStateUpdatedV1
        .of(
          eServiceId = event.eServiceId,
          consumerId = event.consumerId,
          agreementId = event.agreementId.toString,
          state = componentStateToProtobuf(event.state)
        )
    )

  implicit def purposeStateUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[PurposeStateUpdatedV1, PurposeStateUpdated] =
    event =>
      for {
        versionId <- event.versionId.toUUID.toEither
        state     <- protobufToComponentState(event.state)
      } yield PurposeStateUpdated(purposeId = event.purposeId, versionId = versionId, state = state)

  implicit def purposeStateUpdatedV1PersistEventSerializer
    : PersistEventSerializer[PurposeStateUpdated, PurposeStateUpdatedV1] = event =>
    Right[Throwable, PurposeStateUpdatedV1](
      PurposeStateUpdatedV1
        .of(
          purposeId = event.purposeId,
          versionId = event.versionId.toString,
          state = componentStateToProtobuf(event.state)
        )
    )

  implicit def agreementAndEServiceStatesUpdatedV1PersistEventDeserializer
    : PersistEventDeserializer[AgreementAndEServiceStatesUpdatedV1, AgreementAndEServiceStatesUpdated] =
    event =>
      for {
        descriptorId   <- event.descriptorId.toUUID.toEither
        agreementId    <- event.agreementId.toUUID.toEither
        agreementState <- protobufToComponentState(event.agreementState)
        eServiceState  <- protobufToComponentState(event.eServiceState)
      } yield AgreementAndEServiceStatesUpdated(
        eServiceId = event.eServiceId,
        descriptorId = descriptorId,
        consumerId = event.consumerId,
        agreementId = agreementId,
        agreementState = agreementState,
        eServiceState = eServiceState,
        audience = event.audience,
        voucherLifespan = event.voucherLifespan
      )

  implicit def agreementAndEServiceStatesUpdatedV1PersistEventSerializer
    : PersistEventSerializer[AgreementAndEServiceStatesUpdated, AgreementAndEServiceStatesUpdatedV1] = event =>
    Right[Throwable, AgreementAndEServiceStatesUpdatedV1](
      AgreementAndEServiceStatesUpdatedV1
        .of(
          eServiceId = event.eServiceId,
          descriptorId = event.descriptorId.toString,
          consumerId = event.consumerId,
          agreementId = event.agreementId.toString,
          agreementState = componentStateToProtobuf(event.agreementState),
          eServiceState = componentStateToProtobuf(event.eServiceState),
          audience = event.audience,
          voucherLifespan = event.voucherLifespan
        )
    )

  private def keyToEntry(keys: Keys): ErrorOr[Seq[PersistentKeyEntryV1]] =
    keys.toList.traverse { case (kid, pkey) => keyToProtobuf(pkey).map(PersistentKeyEntryV1(kid, _)) }

  private def clientToStateEntry(client: PersistentClient): ErrorOr[StateClientsEntryV1] =
    clientToProtobuf(client).map(StateClientsEntryV1.of(client.id.toString, _))

  private def keyToProtobuf(key: PersistentKey): ErrorOr[PersistentKeyV1] =
    key.createdAt.asFormattedString.toEither.map(createdAt =>
      PersistentKeyV1(
        kid = key.kid,
        name = key.name,
        relationshipId = key.relationshipId.toString,
        encodedPem = key.encodedPem,
        algorithm = key.algorithm,
        use = persistentKeyUseToProtobuf(key.use),
        createdAt = createdAt
      )
    )

  private def clientToProtobuf(client: PersistentClient): ErrorOr[PersistentClientV1] =
    Right(
      PersistentClientV1(
        id = client.id.toString,
        consumerId = client.consumerId.toString,
        name = client.name,
        purposes = client.purposes.map(p => ClientPurposesEntryV1.of(clientStatesChainToProtobuf(p))),
        description = client.description,
        relationships = client.relationships.map(_.toString).toSeq,
        kind = clientKindToProtobufV1(client.kind),
        createdAt = Option(client.createdAt.toMillis)
      )
    )

  private def clientStatesChainToProtobuf(statesChain: PersistentClientStatesChain): ClientStatesChainV1 =
    ClientStatesChainV1.of(
      id = statesChain.id.toString,
      eService = clientEServiceDetailsToProtobuf(statesChain.eService),
      agreement = clientAgreementDetailsToProtobuf(statesChain.agreement),
      purpose = clientPurposeDetailsToProtobuf(statesChain.purpose)
    )

  private def clientEServiceDetailsToProtobuf(details: PersistentClientEServiceDetails): ClientEServiceDetailsV1 =
    ClientEServiceDetailsV1.of(
      eServiceId = details.eServiceId.toString,
      descriptorId = details.descriptorId.toString,
      state = componentStateToProtobuf(details.state),
      audience = details.audience,
      voucherLifespan = details.voucherLifespan
    )

  private def clientAgreementDetailsToProtobuf(details: PersistentClientAgreementDetails): ClientAgreementDetailsV1 =
    ClientAgreementDetailsV1.of(
      eServiceId = details.eServiceId.toString,
      consumerId = details.consumerId.toString,
      agreementId = details.agreementId.toString,
      state = componentStateToProtobuf(details.state)
    )

  private def clientPurposeDetailsToProtobuf(details: PersistentClientPurposeDetails): ClientPurposeDetailsV1 =
    ClientPurposeDetailsV1.of(
      purposeId = details.purposeId.toString,
      versionId = details.versionId.toString,
      state = componentStateToProtobuf(details.state)
    )

  private def componentStateToProtobuf(state: PersistentClientComponentState): ClientComponentStateV1 =
    state match {
      case PersistentClientComponentState.Active   => ClientComponentStateV1.ACTIVE
      case PersistentClientComponentState.Inactive => ClientComponentStateV1.INACTIVE
    }

  private def protoEntryToKey(keys: Seq[PersistentKeyEntryV1]): ErrorOr[Seq[(String, PersistentKey)]] =
    keys.traverse(entry => protobufToKey(entry.value).map(key => (entry.keyId, key)))

  private def protoEntryToClient(client: StateClientsEntryV1): ErrorOr[(String, PersistentClient)] =
    protobufToClient(client.client).map(pc => client.clientId -> pc)

  private def protobufToClient(client: PersistentClientV1): ErrorOr[PersistentClient] = {
    for {
      clientId      <- client.id.toUUID.toEither
      consumerId    <- client.consumerId.toUUID.toEither
      purposes      <- protobufToPurposesEntry(client.purposes)
      relationships <- client.relationships.traverse(_.toUUID).toEither
      kind          <- clientKindFromProtobufV1(client.kind)
      createdAt     <- client.createdAt.traverse(_.toOffsetDateTime).toEither
    } yield PersistentClient(
      id = clientId,
      consumerId = consumerId,
      name = client.name,
      purposes = purposes,
      description = client.description,
      relationships = relationships.toSet,
      kind = kind,
      createdAt = createdAt.getOrElse(DEFAULT_CREATED_AT)
    )
  }

  private def protobufToPurposesEntry(purposes: Seq[ClientPurposesEntryV1]): ErrorOr[Seq[PersistentClientStatesChain]] =
    purposes.traverse(p => protobufToClientStatesChain(p.states))

  private def protobufToClientStatesChain(statesChain: ClientStatesChainV1): ErrorOr[PersistentClientStatesChain] = {
    for {
      uuid      <- statesChain.id.toUUID.toEither
      eService  <- protobufToClientEServiceDetails(statesChain.eService)
      agreement <- protobufToClientAgreementDetails(statesChain.agreement)
      purpose   <- protobufToClientPurposeDetails(statesChain.purpose)
    } yield PersistentClientStatesChain(id = uuid, eService = eService, agreement = agreement, purpose = purpose)
  }

  private def protobufToClientEServiceDetails(
    details: ClientEServiceDetailsV1
  ): ErrorOr[PersistentClientEServiceDetails] =
    for {
      eServiceId   <- details.eServiceId.toUUID.toEither
      descriptorId <- details.descriptorId.toUUID.toEither
      state        <- protobufToComponentState(details.state)
    } yield PersistentClientEServiceDetails(
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      state = state,
      audience = details.audience,
      voucherLifespan = details.voucherLifespan
    )

  private def protobufToClientAgreementDetails(
    details: ClientAgreementDetailsV1
  ): ErrorOr[PersistentClientAgreementDetails] =
    for {
      eServiceId  <- details.eServiceId.toUUID.toEither
      consumerId  <- details.consumerId.toUUID.toEither
      agreementId <- details.agreementId.toUUID.toEither
      state       <- protobufToComponentState(details.state)
    } yield PersistentClientAgreementDetails(
      eServiceId = eServiceId,
      consumerId = consumerId,
      agreementId = agreementId,
      state = state
    )

  private def protobufToClientPurposeDetails(details: ClientPurposeDetailsV1): ErrorOr[PersistentClientPurposeDetails] =
    for {
      purposeId <- details.purposeId.toUUID.toEither
      versionId <- details.versionId.toUUID.toEither
      state     <- protobufToComponentState(details.state)
    } yield PersistentClientPurposeDetails(purposeId = purposeId, versionId = versionId, state = state)

  private def protobufToComponentState(state: ClientComponentStateV1): ErrorOr[PersistentClientComponentState] =
    state match {
      case ClientComponentStateV1.ACTIVE          => Right(PersistentClientComponentState.Active)
      case ClientComponentStateV1.INACTIVE        => Right(PersistentClientComponentState.Inactive)
      case ClientComponentStateV1.Unrecognized(v) =>
        Left(new RuntimeException(s"Unable to deserialize Component State value $v"))
    }

  private def protobufToKey(key: PersistentKeyV1): ErrorOr[PersistentKey] =
    for {
      relationshipId <- Try(UUID.fromString(key.relationshipId)).toEither
      use            <- persistentKeyUseFromProtobuf(key.use)
      createdAt      <- key.createdAt.toOffsetDateTime.toEither
    } yield PersistentKey(
      kid = key.kid,
      name = key.name,
      relationshipId = relationshipId,
      encodedPem = key.encodedPem,
      algorithm = key.algorithm,
      use = use,
      createdAt = createdAt
    )

  def persistentKeyUseToProtobuf(use: PersistentKeyUse): KeyUseV1 = use match {
    case Sig => KeyUseV1.SIG
    case Enc => KeyUseV1.ENC
  }

  def persistentKeyUseFromProtobuf(use: KeyUseV1): ErrorOr[PersistentKeyUse] = use match {
    case KeyUseV1.SIG             => Right(Sig)
    case KeyUseV1.ENC             => Right(Enc)
    case KeyUseV1.Unrecognized(v) => Left(new RuntimeException(s"Unable to deserialize Key Use value $v"))
  }

  def clientKindFromProtobufV1(protobufClientKind: ClientKindV1): Either[Throwable, PersistentClientKind] =
    protobufClientKind match {
      case ClientKindV1.CONSUMER            => Right(Consumer)
      case ClientKindV1.API                 => Right(Api)
      case ClientKindV1.Unrecognized(value) =>
        Left(new RuntimeException(s"Unable to deserialize client kind value $value"))
    }

  def clientKindToProtobufV1(clientKind: PersistentClientKind): ClientKindV1 =
    clientKind match {
      case Consumer => ClientKindV1.CONSUMER
      case Api      => ClientKindV1.API
    }

}
