package it.pagopa.interop.authorizationmanagement.authz

import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.Entity
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiMarshallerImpl._
import it.pagopa.interop.authorizationmanagement.api.impl.KeyApiServiceImpl
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.server.impl.Main.behaviorFactory
import it.pagopa.interop.authorizationmanagement.util.{AuthorizedRoutes, ClusteredScalatestRouteTest}
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.OffsetDateTime

class KeyApiServiceAuthzSpec extends AnyWordSpecLike with ClusteredScalatestRouteTest {

  override val testPersistentEntity: Entity[Command, ShardingEnvelope[Command]] =
    Entity(KeyPersistentBehavior.TypeKey)(behaviorFactory)

  val service: KeyApiServiceImpl =
    KeyApiServiceImpl(testTypedSystem, testAkkaSharding, testPersistentEntity, () => OffsetDateTime.now())

  "Key api operation authorization spec" should {

    "accept authorized roles for createKeys" in {
      val endpoint = AuthorizedRoutes.endpoints("createKeys")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.createKeys("fake", Seq.empty) })
    }

    "accept authorized roles for getClientKeyById" in {
      val endpoint = AuthorizedRoutes.endpoints("getClientKeyById")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getClientKeyById("fake", "fake") })
    }

    "accept authorized roles for getClientKeys" in {
      val endpoint = AuthorizedRoutes.endpoints("getClientKeys")
      validateAuthorization(endpoint, { implicit c: Seq[(String, String)] => service.getClientKeys("fake") })
    }

    "accept authorized roles for deleteClientKeyById" in {
      val endpoint = AuthorizedRoutes.endpoints("deleteClientKeyById")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.deleteClientKeyById("fake", "fake") }
      )
    }

    "accept authorized roles for getEncodedClientKeyById" in {
      val endpoint = AuthorizedRoutes.endpoints("getEncodedClientKeyById")
      validateAuthorization(
        endpoint,
        { implicit c: Seq[(String, String)] => service.getEncodedClientKeyById("fake", "fake") }
      )
    }
  }

}
