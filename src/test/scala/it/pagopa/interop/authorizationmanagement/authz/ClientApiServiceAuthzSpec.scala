package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import com.github.dwickern.macros.NameOf.nameOf
import it.pagopa.interop.authorizationmanagement.api.impl.ClientApiMarshallerImpl._
import it.pagopa.interop.authorizationmanagement.api.impl.ClientApiServiceImpl
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.model.{ClientKind, ClientSeed, PartyRelationshipSeed}
import it.pagopa.interop.authorizationmanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.authorizationmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.USER_ROLES
import it.pagopa.interop.commons.utils.service.UUIDSupplier
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.annotation.nowarn

class ClientApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

  val service: ClientApiServiceImpl =
    ClientApiServiceImpl(
      testTypedSystem,
      testAkkaSharding,
      testPersistentEntity,
      new UUIDSupplier {
        override def get: UUID = UUID.randomUUID()
      }
    )

  "Client api operation authorization spec" should {

    "accept authorized roles for createClient" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.createClient(???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val fakeSeed = ClientSeed(
        consumerId = UUID.randomUUID(),
        name = "fake",
        description = Some("fake"),
        kind = ClientKind.CONSUMER
      )

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.createClient(fakeSeed))
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.createClient(fakeSeed))
    }
    "accept authorized roles for getClient" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.getClient(???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClient("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClient("fake"))
    }
    "accept authorized roles for listClients" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.listClients(???, ???, ???, ???, ???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.listClients(1, 1, None, None, None, None)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.listClients(1, 1, None, None, None, None)
      )
    }
    "accept authorized roles for addRelationship" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.addRelationship(???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      val fakeSeed = PartyRelationshipSeed(relationshipId = UUID.randomUUID())
      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.addRelationship("fake", fakeSeed)
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.addRelationship("fake", fakeSeed)
      )
    }
    "accept authorized roles for deleteClient" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.deleteClient(???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.deleteClient("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.deleteClient("fake"))
    }

    "accept authorized roles for removeClientRelationship" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.removeClientRelationship(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.removeClientRelationship("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.removeClientRelationship("fake", "fake")
      )
    }
    "accept authorized roles for getClientByPurposeId" in {
      @nowarn
      val routeName = nameOf[ClientApiServiceImpl](_.getClientByPurposeId(???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getClientByPurposeId("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.getClientByPurposeId("fake", "fake")
      )
    }
  }
}
