package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiMarshallerImpl._
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiServiceImpl
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.authorizationmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.USER_ROLES
import org.scalatest.wordspec.AnyWordSpecLike

class KeyApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

  val service: KeyApiServiceImpl = KeyApiServiceImpl(testTypedSystem, testAkkaSharding, testPersistentEntity)

  "Key api operation authorization spec" should {

    "accept authorized roles for createKeys" in {
      val endpoint = AuthorizedRoutes.endpoints("createKeys")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.createKeys("fake", Seq.empty)
        )
      })

      // given a fake role, check that its invocation is forbidden

      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.createKeys("fake", Seq.empty)
        )
      })
    }

    "accept authorized roles for getClientKeyById" in {

      val endpoint = AuthorizedRoutes.endpoints("getClientKeyById")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getClientKeyById("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden

      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getClientKeyById("fake", "fake")
        )
      })
    }

    "accept authorized roles for getClientKeys" in {

      val endpoint = AuthorizedRoutes.endpoints("getClientKeys")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClientKeys("fake"))
      })

      // given a fake role, check that its invocation is forbidden

      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClientKeys("fake"))
      })
    }

    "accept authorized roles for deleteClientKeyById" in {

      val endpoint = AuthorizedRoutes.endpoints("deleteClientKeyById")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.deleteClientKeyById("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden

      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.deleteClientKeyById("fake", "fake")
        )
      })
    }

    "accept authorized roles for getEncodedClientKeyById" in {

      val endpoint = AuthorizedRoutes.endpoints("getEncodedClientKeyById")

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(
          contexts.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getEncodedClientKeyById("fake", "fake")
        )
      })

      // given a fake role, check that its invocation is forbidden

      endpoint.invalidRoles.foreach(contexts => {
        implicit val invalidCtx = contexts
        invalidRoleCheck(
          invalidCtx.toMap.get(USER_ROLES).toString,
          endpoint.asRequest,
          service.getEncodedClientKeyById("fake", "fake")
        )
      })
    }
  }

}
