package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import com.github.dwickern.macros.NameOf.nameOf
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiMarshallerImpl._
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiServiceImpl
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.authorizationmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import it.pagopa.interop.commons.utils.USER_ROLES
import org.scalatest.wordspec.AnyWordSpecLike

import scala.annotation.nowarn

class KeyApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

  val service: KeyApiServiceImpl = KeyApiServiceImpl(testTypedSystem, testAkkaSharding, testPersistentEntity)

  "Key api operation authorization spec" should {

    "accept authorized roles for createKeys" in {
      @nowarn
      val routeName = nameOf[KeyApiServiceImpl](_.createKeys(???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

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
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.createKeys("fake", Seq.empty)
      )
    }

    "accept authorized roles for getClientKeyById" in {
      @nowarn
      val routeName = nameOf[KeyApiServiceImpl](_.getClientKeyById(???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

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
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.getClientKeyById("fake", "fake")
      )
    }

    "accept authorized roles for getClientKeys" in {
      @nowarn
      val routeName = nameOf[KeyApiServiceImpl](_.getClientKeys(???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

      // for each role of this route, it checks if it is properly authorized
      endpoint.rolesInContexts.foreach(contexts => {
        implicit val ctx = contexts
        validRoleCheck(contexts.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClientKeys("fake"))
      })

      // given a fake role, check that its invocation is forbidden
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(invalidCtx.toMap.get(USER_ROLES).toString, endpoint.asRequest, service.getClientKeys("fake"))
    }

    "accept authorized roles for deleteClientKeyById" in {
      @nowarn
      val routeName = nameOf[KeyApiServiceImpl](_.deleteClientKeyById(???, ???)(???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

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
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.deleteClientKeyById("fake", "fake")
      )
    }

    "accept authorized roles for getEncodedClientKeyById" in {
      @nowarn
      val routeName = nameOf[KeyApiServiceImpl](_.getEncodedClientKeyById(???, ???)(???, ???, ???))
      val endpoint  = AuthorizedRoutes.endpoints(routeName)

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
      implicit val invalidCtx = endpoint.contextsWithInvalidRole
      invalidRoleCheck(
        invalidCtx.toMap.get(USER_ROLES).toString,
        endpoint.asRequest,
        service.getEncodedClientKeyById("fake", "fake")
      )
    }
  }

}
