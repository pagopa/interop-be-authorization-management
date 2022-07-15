package it.pagopa.interop.authorizationmanagement.model.persistence.serializer

import cats.implicits._
import org.scalacheck.Properties
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

class PersistentSerializationSpec extends Properties("PersistenceSerializer") {

  property("tofromTime is identity") = forAll(PersistentSerializationSpec.offsetDatetimeGen) { case (time, _) =>
    toTime(fromTime(time)) == time
  }

  property("fromtoTime is identity") = forAll(PersistentSerializationSpec.offsetDatetimeGen) { case (_, timeString) =>
    fromTime(toTime(timeString)) == timeString
  }

  property("toTime deserializes correctly") = forAll(PersistentSerializationSpec.offsetDatetimeGen) {
    case (time, timeString) =>
      toTime(timeString) == time
  }

  property("fromTime serialized correctly") = forAll(PersistentSerializationSpec.offsetDatetimeGen) {
    case (time, timeString) =>
      fromTime(time) == timeString
  }

  property("State is correctly deserialized") = forAll(PersistentSerializationSpec.stateGenerator) {
    case (state, stateV1) => PersistEventDeserializer.from[StateV1, State](stateV1) == Right(state)
  }

  // property("State is correctly serialized") = forAll(PersistentSerializationSpec.stateGenerator) {
  //   case (state, stateV1) =>
  //     PersistEventSerializer.to[State, StateV1](state) == Right(stateV1)
  // }

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
    .map { case (pstates, statesV1) =>
      (
        PersistentClientPurpose(pstates.purpose.purposeId, pstates),
        ClientPurposesEntryV1(statesV1.purpose.purposeId, statesV1)
      )
    }

  val persistentClientPurposesGen
    : Gen[(PersistentClientPurposes.PersistentClientPurposes, Seq[ClientPurposesEntryV1])] =
    Gen.nonEmptyListOf(persistentClientPurposeGen).flatMap { list =>
      val (pClients, clientsV1)                                       = list.separate
      val purposes: PersistentClientPurposes.PersistentClientPurposes =
        pClients.map(cp => (cp.statesChain.purpose.purposeId.toString(), cp.statesChain)).toMap
      (purposes, clientsV1)
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

  def sizedClientsGenerator(n: Int): Gen[List[((String, PersistentClient), (String, PersistentClientV1))]] =
    Gen.containerOfN[List, (PersistentClient, PersistentClientV1)](n, persistentClientGen).map { list =>
      list.map { case (pc, pcv1) => (pc.id.toString -> pc, pcv1.id -> pcv1) }
    }

  def sizedKeysGenerator(n: Int): Gen[(Map[String, PersistentKey], Map[String, PersistentKeyV1])] =
    Gen
      .containerOfN[List, ((String, PersistentKey), (String, PersistentKeyV1))](
        n,
        persistentKeyGen.map { case (pk, pkv1) => (pk.kid -> pk, pkv1.kid -> pkv1) }
      )
      .map { list =>
        val (pKeys, pKeysV1) = list.separate
        (pKeys.toMap, pKeysV1.toMap)
      }

  val stateGenerator: Gen[(State, StateV1)] = for {
    cardinality    <- Gen.chooseNum(1, 10)
    mapCardinality <- Gen.chooseNum(1, 2)
    clientsList    <- sizedClientsGenerator(cardinality)
    (clients, clientsV1)       = clientsList.separate
    (clientsMap, clientsV1Map) = (clients.toMap, clientsV1.map { case (k, v) => StateClientsEntryV1(k, v) }.toSeq)
    (keys, keysv1) <- Gen
      .sequence(clientsList.map { case ((pcid, _), (pcv1id, _)) =>
        sizedKeysGenerator(mapCardinality).map { case (keys, keysv1) => ((pcid, keys), (pcv1id, keysv1)) }
      })
      .map { list =>
        val (a, b) = list.asScala.toList.separate
        (
          a.toMap,
          b.map { case (k, v) => StateKeysEntryV1(k, v.map { case (x, y) => PersistentKeyEntryV1(x, y) }.toSeq) }
        )
      }
  } yield (State(keys, clientsMap), StateV1(keysv1, clientsV1Map))
}
