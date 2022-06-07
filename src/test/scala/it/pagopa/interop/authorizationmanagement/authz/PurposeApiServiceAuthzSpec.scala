package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import com.github.dwickern.macros.NameOf.nameOf
import it.pagopa.interop.authorizationmanagement.api.impl.PurposeApiMarshallerImpl._
import it.pagopa.interop.authorizationmanagement.api.impl.PurposeApiServiceImpl
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.model._
import it.pagopa.interop.authorizationmanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.authorizationmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.USER_ROLES
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.annotation.nowarn

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
      @nowarn
      val routeName = nameOf[PurposeApiServiceImpl](_.addClientPurpose(???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val fakeSeed = PurposeSeed(
        purposeId = UUID.randomUUID(),
        states = ClientStatesChainSeed(
          eservice = ClientEServiceDetailsSeed(
            eserviceId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE,
            audience = Seq.empty,
            voucherLifespan = 1000
          ),
          agreement = ClientAgreementDetailsSeed(
            eserviceId = UUID.randomUUID(),
            consumerId = UUID.randomUUID(),
            state = ClientComponentState.ACTIVE
          ),
          purpose = ClientPurposeDetailsSeed(purposeId = UUID.randomUUID(), state = ClientComponentState.ACTIVE)
        )
      )

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.addClientPurpose("fake", fakeSeed)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.addClientPurpose("fake", fakeSeed)
      )
    }

    "accept authorized roles for removeClientPurpose" in {
      @nowarn
      val routeName = nameOf[PurposeApiServiceImpl](_.removeClientPurpose(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.removeClientPurpose("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.removeClientPurpose("fake", "fake")
      )
    }

    "accept authorized roles for updateEServiceState" in {
      @nowarn
      val routeName = nameOf[PurposeApiServiceImpl](_.updateEServiceState(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val fakeUpdatePayload =
        ClientEServiceDetailsUpdate(state = ClientComponentState.ACTIVE, audience = Seq.empty, voucherLifespan = 1)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.updateEServiceState("fake", fakeUpdatePayload)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.updateEServiceState("fake", fakeUpdatePayload)
      )
    }

    "accept authorized roles for updateAgreementState" in {
      @nowarn
      val routeName = nameOf[PurposeApiServiceImpl](_.updateAgreementState(???, ???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val fakeUpdatePayload = ClientAgreementDetailsUpdate(state = ClientComponentState.ACTIVE)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.updateAgreementState("fake", "fake", fakeUpdatePayload)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.updateAgreementState("fake", "fake", fakeUpdatePayload)
      )
    }

    "accept authorized roles for updatePurposeState" in {
      @nowarn
      val routeName = nameOf[PurposeApiServiceImpl](_.updatePurposeState(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val fakeUpdatePayload = ClientPurposeDetailsUpdate(state = ClientComponentState.ACTIVE)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.updatePurposeState("fake", fakeUpdatePayload)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.updatePurposeState("fake", fakeUpdatePayload)
      )
    }
  }
}
