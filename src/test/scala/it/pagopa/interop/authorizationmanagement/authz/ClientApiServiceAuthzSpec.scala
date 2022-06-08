package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
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

      val endpoint = AuthorizedRoutes.endpoints("createClient")

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
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.createClient(fakeSeed))
      })
    }

    "accept authorized roles for getClient" in {

      val endpoint = AuthorizedRoutes.endpoints("getClient")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClient("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClient("fake"))
      })
    }
    "accept authorized roles for listClients" in {

      val endpoint = AuthorizedRoutes.endpoints("listClients")

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
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.listClients(1, 1, None, None, None, None)
        )
      })
    }
    "accept authorized roles for addRelationship" in {

      val endpoint = AuthorizedRoutes.endpoints("addRelationship")

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
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.addRelationship("fake", fakeSeed)
        )
      })
    }
    "accept authorized roles for deleteClient" in {

      val endpoint = AuthorizedRoutes.endpoints("deleteClient")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.deleteClient("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.deleteClient("fake"))
      })
    }

    "accept authorized roles for removeClientRelationship" in {

      val endpoint = AuthorizedRoutes.endpoints("removeClientRelationship")

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
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.removeClientRelationship("fake", "fake")
        )
      })
    }
    "accept authorized roles for getClientByPurposeId" in {

      val endpoint = AuthorizedRoutes.endpoints("getClientByPurposeId")

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
      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getClientByPurposeId("fake", "fake")
        )
      })
    }
  }
}
