package it.pagopa.pdnd.interop.uservice.keymanagement

import akka.actor
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.typed.PersistenceId
import com.nimbusds.jose.util.Base64
import it.pagopa.pdnd.interop.uservice.keymanagement.api._
import it.pagopa.pdnd.interop.uservice.keymanagement.api.impl._
import it.pagopa.pdnd.interop.uservice.keymanagement.common.system.Authenticator
import it.pagopa.pdnd.interop.uservice.keymanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.pdnd.interop.uservice.keymanagement.model.{Client, KeysResponse}
import it.pagopa.pdnd.interop.uservice.keymanagement.server.Controller
import it.pagopa.pdnd.interop.uservice.keymanagement.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory
import spray.json.DefaultJsonProtocol

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait SpecHelper extends SpecConfiguration with MockFactory with SprayJsonSupport with DefaultJsonProtocol {
  self: ScalaTestWithActorTestKit =>

  val mockUUIDSupplier: UUIDSupplier = mock[UUIDSupplier]
  val healthApiMock: HealthApi       = mock[HealthApi]

  val keyApiMarshaller: KeyApiMarshaller       = new KeyApiMarshallerImpl
  val clientApiMarshaller: ClientApiMarshaller = new ClientApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None
  val wrappingDirective: AuthenticationDirective[Unit] =
    SecurityDirectives.authenticateBasic("SecurityRealm", Authenticator)

  val sharding: ClusterSharding = ClusterSharding(system)

  val httpSystem: ActorSystem[Any] =
    ActorSystem(Behaviors.ignore[Any], name = system.name, config = system.settings.config)
  implicit val executionContext: ExecutionContextExecutor = httpSystem.executionContext
  val classicSystem: actor.ActorSystem                    = httpSystem.classicSystem

  def startServer(): Unit = {
    val persistentEntity: Entity[Command, ShardingEnvelope[Command]] =
      Entity(typeKey = KeyPersistentBehavior.TypeKey) { entityContext =>
        KeyPersistentBehavior(
          entityContext.shard,
          PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)
        )
      }

    Cluster(system).manager ! Join(Cluster(system).selfMember.address)
    sharding.init(persistentEntity)

    val keyApi =
      new KeyApi(new KeyApiServiceImpl(system, sharding, persistentEntity), keyApiMarshaller, wrappingDirective)

    val clientApi = new ClientApi(
      new ClientApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier),
      clientApiMarshaller,
      wrappingDirective
    )

    controller = Some(new Controller(clientApi, healthApiMock, keyApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  def shutdownServer(): Unit = {
    println("****** Shutting down server ********")
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
    println("Server shut down - Resources cleaned")
  }

  def createClient(id: UUID, agreementId: UUID): Client = {
    (() => mockUUIDSupplier.get).expects().returning(id).once()

    val agreementUuid = agreementId
    val description   = s"New Client ${id.toString}"

    val data =
      s"""{
         |  "agreementId": "${agreementUuid.toString}",
         |  "description": "$description"
         |}""".stripMargin

    val response = request(uri = s"$serviceURL/clients", method = HttpMethods.POST, data = Some(data))

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[Client], Duration.Inf)
  }

  def generateEncodedKey(): String = {
    import java.security.KeyPairGenerator
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(1024)
    val keyPair = gen.generateKeyPair

    val publicKeyBytes = keyPair.getPublic.getEncoded

    val publicKeyContent = Base64.encode(publicKeyBytes).toString

    val header = "-----BEGIN PUBLIC KEY-----"
    val footer = "-----END PUBLIC KEY-----"

    val keyRows = (header :: publicKeyContent.grouped(64).toList) :+ footer
    val key     = keyRows.mkString(System.lineSeparator)

    Base64.encode(key).toString
  }

  def createKey(clientId: UUID, operatorId: UUID): KeysResponse = {

    val data =
      s"""
         |[
         |  {
         |    "operatorId": "${operatorId.toString}",
         |    "key": "${generateEncodedKey()}",
         |    "use": "sig",
         |    "alg": "123"
         |  }
         |]
         |""".stripMargin

    val response = request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[KeysResponse], Duration.Inf)
  }

  def addOperator(clientId: UUID, operatorId: UUID): Client = {
    val requestBody = s"""{"operatorId": "${operatorId.toString}"}"""

    val response = request(
      uri = s"$serviceURL/clients/${clientId.toString}/operators",
      method = HttpMethods.POST,
      data = Some(requestBody)
    )

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[Client], Duration.Inf)
  }

  def request(uri: String, method: HttpMethod, data: Option[String] = None): HttpResponse = {
    val httpRequest: HttpRequest = HttpRequest(uri = uri, method = method)

    val requestWithEntity: HttpRequest =
      data.fold(httpRequest)(d => httpRequest.withEntity(HttpEntity(ContentTypes.`application/json`, d)))

    Await.result(Http()(classicSystem).singleRequest(requestWithEntity), Duration.Inf)
  }
}
