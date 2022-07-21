package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import it.pagopa.interop.authorizationmanagement.model.client._
import it.pagopa.interop.authorizationmanagement.model.key._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.client._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.key._
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.jdk.CollectionConverters._
import it.pagopa.interop.authorizationmanagement.model.persistence.State
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.state.StateV1
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.state.StateKeysEntryV1
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.state.StateClientsEntryV1
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer._
import munit.ScalaCheckSuite
import cats.kernel.Eq
import PersistentSerializationSpec._
import it.pagopa.interop.authorizationmanagement.model.persistence.PersistenceTypes._
import it.pagopa.interop.authorizationmanagement.model.persistence._
import it.pagopa.interop.authorizationmanagement.model.persistence.serializer.v1.events._

class PersistentSerializationSpec extends ScalaCheckSuite {

  property("State is correctly deserialized") {
    forAll(stateGenerator) { case (state, stateV1) =>
      PersistEventDeserializer.from[StateV1, State](stateV1) == Right(state)
    }
  }

  property("KeysAdded is correctly deserialized") {
    forAll(keysAddedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[KeysAddedV1, KeysAdded](stateV1) == Right(state)
    }
  }
  property("KeyDeleted is correctly deserialized") {
    forAll(keyDeletedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[KeyDeletedV1, KeyDeleted](stateV1) == Right(state)
    }
  }
  property("ClientAdded is correctly deserialized") {
    forAll(clientAddedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[ClientAddedV1, ClientAdded](stateV1) == Right(state)
    }
  }
  property("ClientDeleted is correctly deserialized") {
    forAll(clientDeletedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[ClientDeletedV1, ClientDeleted](stateV1) == Right(state)
    }
  }
  property("RelationshipAdded is correctly deserialized") {
    forAll(relationshipAddedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[RelationshipAddedV1, RelationshipAdded](stateV1) == Right(state)
    }
  }
  property("RelationshipRemoved is correctly deserialized") {
    forAll(relationshipRemovedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[RelationshipRemovedV1, RelationshipRemoved](stateV1) == Right(state)
    }
  }
  property("ClientPurposeAdded is correctly deserialized") {
    forAll(clientPurposeAddedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[ClientPurposeAddedV1, ClientPurposeAdded](stateV1) == Right(state)
    }
  }
  property("EServiceStateUpdated is correctly deserialized") {
    forAll(eServiceStateUpdatedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[EServiceStateUpdatedV1, EServiceStateUpdated](stateV1) == Right(state)
    }
  }
  property("AgreementStateUpdated is correctly deserialized") {
    forAll(agreementStateUpdatedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[AgreementStateUpdatedV1, AgreementStateUpdated](stateV1) == Right(state)
    }
  }
  property("PurposeStateUpdated is correctly deserialized") {
    forAll(purposeStateUpdatedGen) { case (state, stateV1) =>
      PersistEventDeserializer.from[PurposeStateUpdatedV1, PurposeStateUpdated](stateV1) == Right(state)
    }
  }

  // * Equality has been customized to do not get affected
  // * by different collection kind and order
  property("State is correctly serialized") {
    forAll(stateGenerator) { case (state, stateV1) =>
      PersistEventSerializer.to[State, StateV1](state) === Right(stateV1)
    }
  }

  property("KeysAdded is correctly serialized") {
    forAll(keysAddedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[KeysAdded, KeysAddedV1](state) === Right(stateV1)
    }
  }
  property("KeyDeleted is correctly serialized") {
    forAll(keyDeletedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[KeyDeleted, KeyDeletedV1](state) == Right(stateV1)
    }
  }
  property("ClientAdded is correctly serialized") {
    forAll(clientAddedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[ClientAdded, ClientAddedV1](state) == Right(stateV1)
    }
  }
  property("ClientDeleted is correctly serialized") {
    forAll(clientDeletedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[ClientDeleted, ClientDeletedV1](state) == Right(stateV1)
    }
  }
  property("RelationshipAdded is correctly serialized") {
    forAll(relationshipAddedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[RelationshipAdded, RelationshipAddedV1](state) == Right(stateV1)
    }
  }
  property("RelationshipRemoved is correctly serialized") {
    forAll(relationshipRemovedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[RelationshipRemoved, RelationshipRemovedV1](state) == Right(stateV1)
    }
  }
  property("ClientPurposeAdded is correctly serialized") {
    forAll(clientPurposeAddedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[ClientPurposeAdded, ClientPurposeAddedV1](state) == Right(stateV1)
    }
  }
  property("EServiceStateUpdated is correctly serialized") {
    forAll(eServiceStateUpdatedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[EServiceStateUpdated, EServiceStateUpdatedV1](state) == Right(stateV1)
    }
  }
  property("AgreementStateUpdated is correctly serialized") {
    forAll(agreementStateUpdatedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[AgreementStateUpdated, AgreementStateUpdatedV1](state) == Right(stateV1)
    }
  }
  property("PurposeStateUpdated is correctly serialized") {
    forAll(purposeStateUpdatedGen) { case (state, stateV1) =>
      PersistEventSerializer.to[PurposeStateUpdated, PurposeStateUpdatedV1](state) == Right(stateV1)
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

  val persistentKeyUseGen: Gen[(PersistentKeyUse, KeyUseV1)] =
    Gen.oneOf[(PersistentKeyUse, KeyUseV1)]((Sig, KeyUseV1.SIG), (Enc, KeyUseV1.ENC))

  val persistentKeyGen: Gen[(PersistentKey, PersistentKeyV1)] = for {
    relationshipId                               <- Gen.uuid
    kid                                          <- stringGen
    name                                         <- stringGen
    encodedPem                                   <- stringGen
    algorithm                                    <- stringGen
    (puse, usev1)                                <- persistentKeyUseGen
    (creationTimestamp, creationTimestampString) <- offsetDatetimeGen
  } yield (
    PersistentKey(
      relationshipId = relationshipId,
      kid = kid,
      name = name,
      encodedPem = encodedPem,
      algorithm = algorithm,
      use = puse,
      creationTimestamp = creationTimestamp
    ),
    PersistentKeyV1(
      relationshipId = relationshipId.toString(),
      kid = kid,
      encodedPem = encodedPem,
      use = usev1,
      algorithm = algorithm,
      creationTimestamp = creationTimestampString,
      name = name
    )
  )

  val persistentClientComponentStateGen: Gen[(PersistentClientComponentState, ClientComponentStateV1)] =
    Gen.oneOf[(PersistentClientComponentState, ClientComponentStateV1)](
      (PersistentClientComponentState.Active, ClientComponentStateV1.ACTIVE),
      (PersistentClientComponentState.Inactive, ClientComponentStateV1.INACTIVE)
    )

  val persistentClientAgreementDetailsGen: Gen[(PersistentClientAgreementDetails, ClientAgreementDetailsV1)] = for {
    eserviceId        <- Gen.uuid
    consumerId        <- Gen.uuid
    agreementId       <- Gen.uuid
    (pstate, stateV1) <- persistentClientComponentStateGen
  } yield (
    PersistentClientAgreementDetails(eserviceId, consumerId, agreementId, pstate),
    ClientAgreementDetailsV1(eserviceId.toString(), consumerId.toString(), agreementId.toString(), stateV1)
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
    eserviceId                <- Gen.uuid
    descriptorId              <- Gen.uuid
    (pcompstate, compstatev1) <- persistentClientComponentStateGen
    audience                  <- Gen.containerOf[List, String](stringGen)
    voucherLifespan           <- Gen.chooseNum(0, 20000)
  } yield (
    PersistentClientEServiceDetails(eserviceId, descriptorId, pcompstate, audience, voucherLifespan),
    ClientEServiceDetailsV1(
      eServiceId = eserviceId.toString(),
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

  val persistentClientGen: Gen[(PersistentClient, PersistentClientV1)] = for {
    id                       <- Gen.uuid
    consumerId               <- Gen.uuid
    name                     <- stringGen
    (ppurposesC, purposeCV1) <- persistentClientPurposesGen
    description              <- stringGen.map(Option(_).filter(_.nonEmpty))
    relations                <- Gen.containerOf[Set, UUID](Gen.uuid)
    (pkind, kindV1)          <- persistentClientKindGen
  } yield (
    PersistentClient(id, consumerId, name, ppurposesC, description, relations, pkind),
    PersistentClientV1(
      id = id.toString(),
      consumerId = consumerId.toString(),
      name = name,
      description = description,
      relationships = relations.map(_.toString()).toSeq,
      purposes = purposeCV1,
      kind = kindV1
    )
  )

  def sizedClientsGenerator(n: Int): Gen[(Map[String, PersistentClient], List[StateClientsEntryV1])] =
    Gen.containerOfN[List, (PersistentClient, PersistentClientV1)](n, persistentClientGen).map {
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

  val stateGenerator: Gen[(State, StateV1)] = for {
    cardinality                <- Gen.chooseNum(1, 10)
    mapCardinality             <- Gen.chooseNum(1, 2)
    (clientsMap, clientsV1Map) <- sizedClientsGenerator(cardinality)
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

  val clientAddedGen: Gen[(ClientAdded, ClientAddedV1)] = persistentClientGen.map { case (pc, pcv1) =>
    (ClientAdded(pc), ClientAddedV1(pcv1))
  }

  val clientDeletedGen: Gen[(ClientDeleted, ClientDeletedV1)] =
    stringGen.map(s => (ClientDeleted(s), ClientDeletedV1(s)))

  val relationshipAddedGen: Gen[(RelationshipAdded, RelationshipAddedV1)] = for {
    (pc, pcv1) <- persistentClientGen
    uuid       <- Gen.uuid
  } yield (RelationshipAdded(pc, uuid), RelationshipAddedV1(pcv1, uuid.toString()))

  val relationshipRemovedGen: Gen[(RelationshipRemoved, RelationshipRemovedV1)] = for {
    clientId       <- stringGen
    relationshipId <- stringGen
  } yield (RelationshipRemoved(clientId, relationshipId), RelationshipRemovedV1(clientId, relationshipId))

  val clientPurposeAddedGen: Gen[(ClientPurposeAdded, ClientPurposeAddedV1)] = for {
    clientId         <- stringGen
    (chain, chainV1) <- persistentClientStatesChainGen
  } yield (ClientPurposeAdded(clientId, chain), ClientPurposeAddedV1(clientId, chainV1))

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

  implicit val persistenClientEq: Eq[PersistentClientV1] = Eq.instance { case (result, expected) =>
    result.consumerId === expected.consumerId &&
    result.description === expected.description &&
    result.id === expected.id &&
    result.kind == expected.kind &&
    result.name === expected.name &&
    result.purposes.toList.sortBy(_.states.purpose.purposeId) == expected.purposes.toList.sortBy(
      _.states.purpose.purposeId
    ) &&
    result.relationships.toList.sorted === expected.relationships.toList.sorted
  }

  implicit val stateV1Equality: Eq[StateV1] = Eq.instance { case (result, expected) =>
    val keysEquality: Boolean =
      result.keys
        .map(s => StateKeysEntryV1(s.clientId, s.keyEntries.sortBy(_.keyId)))
        .toList
        .sortBy(_.clientId) == expected.keys
        .map(s => StateKeysEntryV1(s.clientId, s.keyEntries.sortBy(_.keyId)))
        .toList
        .sortBy(_.clientId)

    val clientsEquality: Boolean =
      result.clients.toList.sortBy(_.clientId).zip(expected.clients.toList.sortBy(_.clientId)).forall { case (a, b) =>
        a.clientId == b.clientId && a.client === b.client
      }

    keysEquality && clientsEquality
  }

  implicit val keysAddedV1Eq: Eq[KeysAddedV1] = Eq.instance { case (a, b) =>
    a.clientId == b.clientId && a.keys.sortBy(_.keyId) == b.keys.sortBy(_.keyId)
  }

  implicit val throwableEq: Eq[Throwable] = Eq.fromUniversalEquals

}
