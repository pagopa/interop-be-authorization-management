package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen

import java.time.{OffsetDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import munit.ScalaCheckSuite
import PersistentSerializationSpec._

import scala.jdk.CollectionConverters._
import it.pagopa.interop.authorizationmanagement.model.key._
import it.pagopa.interop.authorizationmanagement.model.client._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.key._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.state._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.client._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.events._
import com.softwaremill.diffx.munit.DiffxAssertions
import com.softwaremill.diffx.generic.auto._
import com.softwaremill.diffx.Diff

import scala.reflect.runtime.universe.{TypeTag, typeOf}

class PersistentSerializationSpec extends ScalaCheckSuite with DiffxAssertions {

  serdeCheck[State, StateV1](stateGenWithCreatedAt, _.sorted)
  serdeCheck[KeysAdded, KeysAddedV1](keysAddedGen, _.sorted)
  serdeCheck[KeyDeleted, KeyDeletedV1](keyDeletedGen)
  serdeCheck[ClientAdded, ClientAddedV1](clientAddedGenWithCreatedAt)
  serdeCheck[ClientDeleted, ClientDeletedV1](clientDeletedGen)
  serdeCheck[RelationshipAdded, RelationshipAddedV1](relationshipAddedGenWithCreatedAt)
  serdeCheck[RelationshipRemoved, RelationshipRemovedV1](relationshipRemovedGen)
  serdeCheck[ClientPurposeAdded, ClientPurposeAddedV1](clientPurposeAddedGen)
  serdeCheck[ClientPurposeRemoved, ClientPurposeRemovedV1](clientPurposeRemovedGen)
  serdeCheck[EServiceStateUpdated, EServiceStateUpdatedV1](eServiceStateUpdatedGen)
  serdeCheck[AgreementStateUpdated, AgreementStateUpdatedV1](agreementStateUpdatedGen)
  serdeCheck[PurposeStateUpdated, PurposeStateUpdatedV1](purposeStateUpdatedGen)
  serdeCheck[AgreementAndEServiceStatesUpdated, AgreementAndEServiceStatesUpdatedV1](
    agreementAndEServiceStatesUpdatedGen
  )

  deserCheck[State, StateV1](stateGenWithCreatedAt)
  deserCheck[State, StateV1](stateGenNoCreatedAt)
  deserCheck[KeysAdded, KeysAddedV1](keysAddedGen)
  deserCheck[KeyDeleted, KeyDeletedV1](keyDeletedGen)
  deserCheck[ClientAdded, ClientAddedV1](clientAddedGenWithCreatedAt)
  deserCheck[ClientAdded, ClientAddedV1](clientAddedGenNoCreatedAt)
  deserCheck[ClientDeleted, ClientDeletedV1](clientDeletedGen)
  deserCheck[RelationshipAdded, RelationshipAddedV1](relationshipAddedGenWithCreatedAt)
  deserCheck[RelationshipAdded, RelationshipAddedV1](relationshipAddedGenNoCreatedAt)
  deserCheck[RelationshipRemoved, RelationshipRemovedV1](relationshipRemovedGen)
  deserCheck[ClientPurposeAdded, ClientPurposeAddedV1](clientPurposeAddedGen)
  deserCheck[ClientPurposeRemoved, ClientPurposeRemovedV1](clientPurposeRemovedGen)
  deserCheck[EServiceStateUpdated, EServiceStateUpdatedV1](eServiceStateUpdatedGen)
  deserCheck[AgreementStateUpdated, AgreementStateUpdatedV1](agreementStateUpdatedGen)
  deserCheck[PurposeStateUpdated, PurposeStateUpdatedV1](purposeStateUpdatedGen)
  deserCheck[AgreementAndEServiceStatesUpdated, AgreementAndEServiceStatesUpdatedV1](
    agreementAndEServiceStatesUpdatedGen
  )

  // TODO move me in commons
  def serdeCheck[A: TypeTag, B](gen: Gen[(A, B)], adapter: B => B = identity[B](_))(implicit
    e: PersistEventSerializer[A, B],
    loc: munit.Location,
    d: => Diff[Either[Throwable, B]]
  ): Unit = property(s"${typeOf[A].typeSymbol.name.toString} is correctly serialized") {
    forAll(gen) { case (state, stateV1) =>
      implicit val diffX: Diff[Either[Throwable, B]] = d
      assertEqual(PersistEventSerializer.to[A, B](state).map(adapter), Right(stateV1).map(adapter))
    }
  }

  // TODO move me in commons
  def deserCheck[A, B: TypeTag](
    gen: Gen[(A, B)]
  )(implicit e: PersistEventDeserializer[B, A], loc: munit.Location, d: => Diff[Either[Throwable, A]]): Unit =
    property(s"${typeOf[B].typeSymbol.name.toString} is correctly deserialized") {
      forAll(gen) { case (state, stateV1) =>
        // * This is declared lazy in the signature to avoid a MethodTooBigException
        implicit val diffX: Diff[Either[Throwable, A]] = d
        assertEqual(PersistEventDeserializer.from[B, A](stateV1), Right(state))
      }
    }

}

object PersistentSerializationSpec {

  val stringGen: Gen[String] = for {
    n <- Gen.chooseNum(4, 100)
    s <- Gen.containerOfN[List, Char](n, Gen.alphaNumChar)
  } yield s.foldLeft("")(_ + _)

  val offsetDatetimeGen: Gen[(OffsetDateTime, String)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now = OffsetDateTime.now()
    time <- Gen.oneOf(now.minusSeconds(n), now.plusSeconds(n))
  } yield (time, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  val offsetDatetimeLongGen: Gen[(OffsetDateTime, Long)] = for {
    n <- Gen.chooseNum(0, 10000L)
    now      = OffsetDateTime.now(ZoneOffset.UTC)
    // Truncate to millis precision
    nowMills = now.withNano(now.getNano - (now.getNano % 1000000))
    time <- Gen.oneOf(nowMills.minusSeconds(n), nowMills.plusSeconds(n))
  } yield (time, time.toInstant.toEpochMilli)

  val defaultCreatedAt: OffsetDateTime = OffsetDateTime.of(2023, 4, 18, 12, 0, 0, 0, ZoneOffset.UTC)

  val persistentKeyUseGen: Gen[(PersistentKeyUse, KeyUseV1)] =
    Gen.oneOf[(PersistentKeyUse, KeyUseV1)]((Sig, KeyUseV1.SIG), (Enc, KeyUseV1.ENC))

  val persistentKeyGen: Gen[(PersistentKey, PersistentKeyV1)] = for {
    relationshipId               <- Gen.uuid
    kid                          <- stringGen
    name                         <- stringGen
    encodedPem                   <- stringGen
    algorithm                    <- stringGen
    (puse, usev1)                <- persistentKeyUseGen
    (createdAt, createdAtString) <- offsetDatetimeGen
  } yield (
    PersistentKey(
      relationshipId = relationshipId,
      kid = kid,
      name = name,
      encodedPem = encodedPem,
      algorithm = algorithm,
      use = puse,
      createdAt = createdAt
    ),
    PersistentKeyV1(
      relationshipId = relationshipId.toString(),
      kid = kid,
      encodedPem = encodedPem,
      use = usev1,
      algorithm = algorithm,
      createdAt = createdAtString,
      name = name
    )
  )

  val persistentClientComponentStateGen: Gen[(PersistentClientComponentState, ClientComponentStateV1)] =
    Gen.oneOf[(PersistentClientComponentState, ClientComponentStateV1)](
      (PersistentClientComponentState.Active, ClientComponentStateV1.ACTIVE),
      (PersistentClientComponentState.Inactive, ClientComponentStateV1.INACTIVE)
    )

  val persistentClientAgreementDetailsGen: Gen[(PersistentClientAgreementDetails, ClientAgreementDetailsV1)] = for {
    eServiceId        <- Gen.uuid
    consumerId        <- Gen.uuid
    agreementId       <- Gen.uuid
    (pstate, stateV1) <- persistentClientComponentStateGen
  } yield (
    PersistentClientAgreementDetails(eServiceId, consumerId, agreementId, pstate),
    ClientAgreementDetailsV1(eServiceId.toString(), consumerId.toString(), agreementId.toString(), stateV1)
  )

  val persistentClientPurposeDetailsGen: Gen[(PersistentClientPurposeDetails, ClientPurposeDetailsV1)] = for {
    purposeId                 <- Gen.uuid
    versionId                 <- Gen.uuid
    (pcompstate, compstatev1) <- persistentClientComponentStateGen
  } yield (
    PersistentClientPurposeDetails(purposeId = purposeId, versionId = versionId, state = pcompstate),
    ClientPurposeDetailsV1(purposeId = purposeId.toString(), versionId = versionId.toString(), state = compstatev1)
  )

  val persistentClientEServiceDetails: Gen[(PersistentClientEServiceDetails, ClientEServiceDetailsV1)] = for {
    eServiceId                <- Gen.uuid
    descriptorId              <- Gen.uuid
    (pcompstate, compstatev1) <- persistentClientComponentStateGen
    audience                  <- Gen.containerOf[List, String](stringGen)
    voucherLifespan           <- Gen.chooseNum(0, 20000)
  } yield (
    PersistentClientEServiceDetails(eServiceId, descriptorId, pcompstate, audience, voucherLifespan),
    ClientEServiceDetailsV1(
      eServiceId = eServiceId.toString(),
      descriptorId = descriptorId.toString(),
      state = compstatev1,
      audience = audience,
      voucherLifespan = voucherLifespan
    )
  )

  val persistentClientStatesChainGen: Gen[(PersistentClientStatesChain, ClientStatesChainV1)] = for {
    id                              <- Gen.uuid
    (pEserviceDet, eserviceDetV1)   <- persistentClientEServiceDetails
    (pClientAgDet, clientAgDetV1)   <- persistentClientAgreementDetailsGen
    (pClientPurDet, clientPurDetV1) <- persistentClientPurposeDetailsGen
  } yield (
    PersistentClientStatesChain(id, pEserviceDet, pClientAgDet, pClientPurDet),
    ClientStatesChainV1(id.toString(), eserviceDetV1, clientAgDetV1, clientPurDetV1)
  )

  val persistentClientPurposeGen: Gen[(PersistentClientPurpose, ClientPurposesEntryV1)] = persistentClientStatesChainGen
    .map { case (pstates, statesV1) => (PersistentClientPurpose(pstates), ClientPurposesEntryV1(statesV1)) }

  val persistentClientPurposesGen: Gen[(Seq[PersistentClientStatesChain], Seq[ClientPurposesEntryV1])] =
    Gen.nonEmptyListOf(persistentClientPurposeGen).flatMap { list =>
      val (pClients, clientsV1) = list.separate
      (pClients.map(_.statesChain), clientsV1)
    }

  val persistentClientKindGen: Gen[(PersistentClientKind, ClientKindV1)] =
    Gen.oneOf[(PersistentClientKind, ClientKindV1)]((Consumer, ClientKindV1.CONSUMER), (Api, ClientKindV1.API))

  val persistentClientGenWithCreatedAt: Gen[(PersistentClient, PersistentClientV1)] = for {
    id                         <- Gen.uuid
    consumerId                 <- Gen.uuid
    name                       <- stringGen
    (ppurposesC, purposeCV1)   <- persistentClientPurposesGen
    description                <- stringGen.map(Option(_).filter(_.nonEmpty))
    relations                  <- Gen.containerOf[Set, UUID](Gen.uuid)
    (pkind, kindV1)            <- persistentClientKindGen
    (createdAt, createdAtLong) <- offsetDatetimeLongGen.map { case (x, y) => (x, y.some) }
  } yield (
    PersistentClient(id, consumerId, name, ppurposesC, description, relations, pkind, createdAt),
    PersistentClientV1(
      id = id.toString,
      consumerId = consumerId.toString,
      name = name,
      description = description,
      relationships = relations.map(_.toString()).toSeq,
      purposes = purposeCV1,
      kind = kindV1,
      createdAt = createdAtLong
    )
  )

  val persistentClientGenNoCreatedAt: Gen[(PersistentClient, PersistentClientV1)] = for {
    id                       <- Gen.uuid
    consumerId               <- Gen.uuid
    name                     <- stringGen
    (ppurposesC, purposeCV1) <- persistentClientPurposesGen
    description              <- stringGen.map(Option(_).filter(_.nonEmpty))
    relations                <- Gen.containerOf[Set, UUID](Gen.uuid)
    (pkind, kindV1)          <- persistentClientKindGen
  } yield (
    PersistentClient(id, consumerId, name, ppurposesC, description, relations, pkind, defaultCreatedAt),
    PersistentClientV1(
      id = id.toString,
      consumerId = consumerId.toString,
      name = name,
      description = description,
      relationships = relations.map(_.toString()).toSeq,
      purposes = purposeCV1,
      kind = kindV1,
      createdAt = None
    )
  )

  def sizedClientsGenerator(
    n: Int,
    persistentClientGen: Gen[(PersistentClient, PersistentClientV1)]
  ): Gen[(Map[String, PersistentClient], List[StateClientsEntryV1])] =
    Gen
      .containerOfN[List, (PersistentClient, PersistentClientV1)](n, persistentClientGen)
      .map {
        _.foldLeft((Map.empty[String, PersistentClient], List.empty[StateClientsEntryV1])) {
          case ((map, list), (pc, pcv1)) => (map + (pc.id.toString -> pc), StateClientsEntryV1(pcv1.id, pcv1) :: list)
        }
      }

  def sizedKeysGenerator(n: Int): Gen[(Keys, List[PersistentKeyEntryV1])] = Gen
    .containerOfN[List, (PersistentKey, PersistentKeyV1)](n, persistentKeyGen)
    .map(_.foldLeft((Map.empty[String, PersistentKey], List.empty[PersistentKeyEntryV1])) {
      case ((map, list), (pk, pkv1)) => (map + (pk.kid -> pk), PersistentKeyEntryV1(pkv1.kid, pkv1) :: list)
    })

  def keysGen(clientsIds: List[String], cardinality: Int): Gen[(Map[String, Keys], List[StateKeysEntryV1])] =
    Gen
      .sequence(
        clientsIds.map(id => sizedKeysGenerator(cardinality).map { case (keys, keysv1) => ((id, keys), (id, keysv1)) })
      )
      .map(_.asScala.toList.foldLeft((Map.empty[String, Keys], List.empty[StateKeysEntryV1])) {
        case ((map, list), (keys, keysV1)) => (map + keys, StateKeysEntryV1(keysV1._1, keysV1._2) :: list)
      })

  val stateGenWithCreatedAt: Gen[(State, StateV1)] = for {
    cardinality                <- Gen.chooseNum(1, 10)
    mapCardinality             <- Gen.chooseNum(1, 2)
    (clientsMap, clientsV1Map) <- sizedClientsGenerator(cardinality, persistentClientGenWithCreatedAt)
    (keys, keysv1)             <- keysGen(clientsMap.keySet.toList, mapCardinality)
  } yield (State(keys, clientsMap), StateV1(keysv1, clientsV1Map))

  val stateGenNoCreatedAt: Gen[(State, StateV1)] = for {
    cardinality                <- Gen.chooseNum(1, 10)
    mapCardinality             <- Gen.chooseNum(1, 2)
    (clientsMap, clientsV1Map) <- sizedClientsGenerator(cardinality, persistentClientGenNoCreatedAt)
    (keys, keysv1)             <- keysGen(clientsMap.keySet.toList, mapCardinality)
  } yield (State(keys, clientsMap), StateV1(keysv1, clientsV1Map))

  val keysAddedGen: Gen[(KeysAdded, KeysAddedV1)] = for {
    clientId         <- stringGen
    cardinality      <- Gen.chooseNum(1, 10)
    (pKeys, pKeysV1) <- sizedKeysGenerator(cardinality)
  } yield (KeysAdded(clientId, pKeys), KeysAddedV1(clientId, pKeysV1))

  val keyDeletedGen: Gen[(KeyDeleted, KeyDeletedV1)] = for {
    clientId      <- stringGen
    keyId         <- stringGen
    (time, times) <- offsetDatetimeGen
  } yield (KeyDeleted(clientId, keyId, time), KeyDeletedV1(clientId, keyId, times))

  val clientAddedGenWithCreatedAt: Gen[(ClientAdded, ClientAddedV1)] = persistentClientGenWithCreatedAt.map {
    case (pc, pcv1) =>
      (ClientAdded(pc), ClientAddedV1(pcv1))
  }

  val clientAddedGenNoCreatedAt: Gen[(ClientAdded, ClientAddedV1)] = persistentClientGenNoCreatedAt.map {
    case (pc, pcv1) =>
      (ClientAdded(pc), ClientAddedV1(pcv1))
  }

  val clientDeletedGen: Gen[(ClientDeleted, ClientDeletedV1)] =
    stringGen.map(s => (ClientDeleted(s), ClientDeletedV1(s)))

  val relationshipAddedGenWithCreatedAt: Gen[(RelationshipAdded, RelationshipAddedV1)] = for {
    (pc, pcv1) <- persistentClientGenWithCreatedAt
    uuid       <- Gen.uuid
  } yield (RelationshipAdded(pc, uuid), RelationshipAddedV1(pcv1, uuid.toString))

  val relationshipAddedGenNoCreatedAt: Gen[(RelationshipAdded, RelationshipAddedV1)] = for {
    (pc, pcv1) <- persistentClientGenNoCreatedAt
    uuid       <- Gen.uuid
  } yield (RelationshipAdded(pc, uuid), RelationshipAddedV1(pcv1, uuid.toString))

  val relationshipRemovedGen: Gen[(RelationshipRemoved, RelationshipRemovedV1)] = for {
    clientId       <- stringGen
    relationshipId <- stringGen
  } yield (RelationshipRemoved(clientId, relationshipId), RelationshipRemovedV1(clientId, relationshipId))

  val clientPurposeAddedGen: Gen[(ClientPurposeAdded, ClientPurposeAddedV1)] = for {
    clientId         <- stringGen
    (chain, chainV1) <- persistentClientStatesChainGen
  } yield (ClientPurposeAdded(clientId, chain), ClientPurposeAddedV1(clientId, chainV1))

  val clientPurposeRemovedGen: Gen[(ClientPurposeRemoved, ClientPurposeRemovedV1)] = for {
    clientId  <- stringGen
    purposeId <- stringGen
  } yield (ClientPurposeRemoved(clientId, purposeId), ClientPurposeRemovedV1(clientId, purposeId))

  val eServiceStateUpdatedGen: Gen[(EServiceStateUpdated, EServiceStateUpdatedV1)] = for {
    eServiceId       <- stringGen
    descriptorId     <- Gen.uuid
    (state, stateV1) <- persistentClientComponentStateGen
    audiences        <- Gen.listOf(stringGen)
    voucherLifespan  <- Gen.posNum[Int]
  } yield (
    EServiceStateUpdated(
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      state = state,
      audience = audiences,
      voucherLifespan = voucherLifespan
    ),
    EServiceStateUpdatedV1(
      eServiceId = eServiceId,
      descriptorId = descriptorId.toString(),
      state = stateV1,
      audience = audiences,
      voucherLifespan = voucherLifespan
    )
  )

  val agreementStateUpdatedGen: Gen[(AgreementStateUpdated, AgreementStateUpdatedV1)] = for {
    eServiceId       <- stringGen
    consumerId       <- stringGen
    agreementId      <- Gen.uuid
    (state, stateV1) <- persistentClientComponentStateGen
  } yield (
    AgreementStateUpdated(eServiceId = eServiceId, consumerId = consumerId, agreementId = agreementId, state = state),
    AgreementStateUpdatedV1(
      eServiceId = eServiceId,
      consumerId = consumerId,
      agreementId = agreementId.toString(),
      state = stateV1
    )
  )

  val purposeStateUpdatedGen: Gen[(PurposeStateUpdated, PurposeStateUpdatedV1)] = for {
    purposeId        <- stringGen
    versionId        <- Gen.uuid
    (state, stateV1) <- persistentClientComponentStateGen
  } yield (
    PurposeStateUpdated(purposeId = purposeId, versionId = versionId, state = state),
    PurposeStateUpdatedV1(purposeId = purposeId, versionId = versionId.toString, state = stateV1)
  )

  val agreementAndEServiceStatesUpdatedGen
    : Gen[(AgreementAndEServiceStatesUpdated, AgreementAndEServiceStatesUpdatedV1)] = for {
    eServiceId                         <- stringGen
    descriptorId                       <- Gen.uuid
    consumerId                         <- stringGen
    agreementId                        <- Gen.uuid
    (agreementState, agreementStateV1) <- persistentClientComponentStateGen
    (eServiceState, eServiceStateV1)   <- persistentClientComponentStateGen
    audience                           <- Gen.containerOf[List, String](stringGen)
    voucherLifespan                    <- Gen.chooseNum(0, 20000)
  } yield (
    AgreementAndEServiceStatesUpdated(
      eServiceId = eServiceId,
      descriptorId = descriptorId,
      consumerId = consumerId,
      agreementId = agreementId,
      agreementState = agreementState,
      eServiceState = eServiceState,
      audience = audience,
      voucherLifespan = voucherLifespan
    ),
    AgreementAndEServiceStatesUpdatedV1(
      eServiceId = eServiceId,
      descriptorId = descriptorId.toString,
      consumerId = consumerId,
      agreementId = agreementId.toString,
      agreementState = agreementStateV1,
      eServiceState = eServiceStateV1,
      audience = audience,
      voucherLifespan = voucherLifespan
    )
  )

  implicit class PimpedPersistentClientV1(val client: PersistentClientV1) extends AnyVal {
    def sorted: PersistentClientV1 = {
      val purposes: List[ClientPurposesEntryV1] = client.purposes.toList.sortBy(_.states.purpose.purposeId)
      val relationships: List[String]           = client.relationships.toList.sorted
      client.copy(purposes = purposes, relationships = relationships)
    }
  }

  implicit class PimpedStateV1(val stateV1: StateV1) extends AnyVal {
    def sorted: StateV1 = {
      val keys: List[StateKeysEntryV1]       = stateV1.keys.toList
        .map(s => StateKeysEntryV1(s.clientId, s.keyEntries.sortBy(_.keyId)))
        .sortBy(_.clientId)
      val clients: List[StateClientsEntryV1] =
        stateV1.clients.map(sce => sce.copy(client = sce.client.sorted)).toList.sortBy(_.clientId)
      stateV1.copy(keys, clients)
    }
  }

  implicit class PimpedKeysAddedV1(val keysAdded: KeysAddedV1) extends AnyVal {
    def sorted: KeysAddedV1 = keysAdded.copy(keys = keysAdded.keys.sortBy(_.keyId))
  }

}
