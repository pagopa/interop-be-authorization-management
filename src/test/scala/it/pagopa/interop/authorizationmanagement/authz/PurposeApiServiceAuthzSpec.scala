package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.authorizationmanagement.api.impl.PurposeApiMarshallerImpl._
import it.pagopa.interop.authorizationmanagement.api.impl.PurposeApiServiceImpl
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.authorizationmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

class PurposeApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {
  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

  val service: PurposeApiServiceImpl =
    PurposeApiServiceImpl(
      testTypedSystem,
      testAkkaSharding,
      testPersistentEntity,
      new UUIDSupplier {
        override def get: UUID = UUID.randomUUID()
      }
    )

  "Purpose api operation authorization spec" should {
    "accept authorized roles for addClientPurpose" in {
      val endpoint = AuthorizedRoutes.endpoints("addClientPurpose")

      val fakeSeed = PurposeSeed(states =
        ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = UUID.randomUUID(),
            descriptorId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE,
            audience = Seq.empty,
            voucherLifespan = 1000
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = UUID.randomUUID(),
            consumerId = UUID.randomUUID(),
            agreementId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(
            purposeId = UUID.randomUUID(),
            versionId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE
          )
        )
      )

      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.addClientPurpose("fake", fakeSeed) }
      )
    }

    "accept authorized roles for removeClientPurpose" in {
      val endpoint = AuthorizedRoutes.endpoints("removeClientPurpose")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.removeClientPurpose("fake", "fake") }
      )
    }

    "accept authorized roles for updateEServiceState" in {
      val endpoint          = AuthorizedRoutes.endpoints("updateEServiceState")
      val fakeUpdatePayload =
        ClientEServiceDetailsUpdate(
          state = ClientComponentState.ACTIVE,
          descriptorId = UUID.randomUUID(),
          audience = Seq.empty,
          voucherLifespan = 1
        )
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateEServiceState("fake", fakeUpdatePayload) }
      )
    }

    "accept authorized roles for updateAgreementState" in {
      val endpoint          = AuthorizedRoutes.endpoints("updateAgreementState")
      val fakeUpdatePayload =
        ClientAgreementDetailsUpdate(agreementId = UUID.randomUUID(), state = ClientComponentState.ACTIVE)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updateAgreementState("fake", "fake", fakeUpdatePayload) }
      )
    }

    "accept authorized roles for updatePurposeState" in {
      val endpoint          = AuthorizedRoutes.endpoints("updatePurposeState")
      val fakeUpdatePayload =
        ClientPurposeDetailsUpdate(versionId = UUID.randomUUID(), state = ClientComponentState.ACTIVE)
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.updatePurposeState("fake", fakeUpdatePayload) }
      )
    }
  }
}
