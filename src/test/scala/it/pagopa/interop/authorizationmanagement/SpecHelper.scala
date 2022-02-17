package it.pagopa.interop.authorizationmanagement

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
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.directives.{AuthenticationDirective, SecurityDirectives}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.persistence.typed.PersistenceId
import com.nimbusds.jose.util.Base64
import it.pagopa.interop.authorizationmanagement.api._
import it.pagopa.interop.authorizationmanagement.api.impl._
import it.pagopa.interop.authorizationmanagement.model.persistence.{Command, KeyPersistentBehavior}
import it.pagopa.interop.authorizationmanagement.model.{Client, KeysResponse, Purpose, PurposeSeed}
import it.pagopa.interop.authorizationmanagement.server.Controller
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils.Authenticator
import it.pagopa.pdnd.interop.commons.utils.service.UUIDSupplier
import org.scalamock.scalatest.MockFactory
import spray.json._

import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

trait SpecHelper extends SpecConfiguration with MockFactory with SprayJsonSupport with DefaultJsonProtocol {
  self: ScalaTestWithActorTestKit =>

  val bearerToken: String               = "token"
  val authorization: Seq[Authorization] = Seq(Authorization(OAuth2BearerToken(bearerToken)))

  val mockUUIDSupplier: UUIDSupplier = mock[UUIDSupplier]
  val healthApiMock: HealthApi       = mock[HealthApi]

  val keyApiMarshaller: KeyApiMarshaller         = KeyApiMarshallerImpl
  val clientApiMarshaller: ClientApiMarshaller   = ClientApiMarshallerImpl
  val purposeApiMarshaller: PurposeApiMarshaller = PurposeApiMarshallerImpl

  var controller: Option[Controller]                 = None
  var bindServer: Option[Future[Http.ServerBinding]] = None

  val wrappingDirective: AuthenticationDirective[Seq[(String, String)]] =
    SecurityDirectives.authenticateOAuth2("SecurityRealm", Authenticator)

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
      new KeyApi(KeyApiServiceImpl(system, sharding, persistentEntity), keyApiMarshaller, wrappingDirective)

    val clientApi = new ClientApi(
      ClientApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier),
      clientApiMarshaller,
      wrappingDirective
    )

    val purposeApi = new PurposeApi(
      PurposeApiServiceImpl(system, sharding, persistentEntity, mockUUIDSupplier),
      purposeApiMarshaller,
      wrappingDirective
    )

    controller = Some(new Controller(clientApi, healthApiMock, keyApi, purposeApi)(classicSystem))

    controller foreach { controller =>
      bindServer = Some(
        Http()(classicSystem)
          .newServerAt("0.0.0.0", 18088)
          .bind(controller.routes)
      )

      Await.result(bindServer.get, 100.seconds)
    }
  }

  def shutdownServer(): Unit = {
    bindServer.foreach(_.foreach(_.unbind()))
    ActorTestKit.shutdown(httpSystem, 5.seconds)
  }

  def createClient(id: UUID, consumerId: UUID): Client = {
    (() => mockUUIDSupplier.get).expects().returning(id).once()

    val consumerUuid = consumerId
    val name         = s"New Client ${id.toString}"
    val description  = s"New Client ${id.toString} description"

    val data =
      s"""{
         |  "consumerId": "${consumerUuid.toString}",
         |  "name": "$name",
         |  "kind": "CONSUMER",
         |  "description": "$description"
         |}""".stripMargin

    val response = request(uri = s"$serviceURL/clients", method = HttpMethods.POST, data = Some(data))

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[Client], Duration.Inf)
  }

  def retrieveClient(clientId: UUID): Client = {
    val result = request(uri = s"$serviceURL/clients/$clientId", method = HttpMethods.GET)
    Await.result(Unmarshal(result).to[Client], Duration.Inf)
  }

  def addPurposeState(clientId: UUID, newState: PurposeSeed, statesChainId: UUID): Purpose = {
    (() => mockUUIDSupplier.get).expects().returning(statesChainId).once()

    val response = request(
      uri = s"$serviceURL/clients/$clientId/purposes",
      method = HttpMethods.POST,
      data = Some(newState.toJson.prettyPrint)
    )

    Await.result(Unmarshal(response).to[Purpose], Duration.Inf)
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

  def createKey(clientId: UUID, relationshipId: UUID): KeysResponse = {

    val data =
      s"""
         |[
         |  {
         |    "relationshipId": "${relationshipId.toString}",
         |    "key": "${generateEncodedKey()}",
         |    "use": "SIG",
         |    "alg": "123"
         |  }
         |]
         |""".stripMargin

    val response =
      request(uri = s"$serviceURL/clients/${clientId.toString}/keys", method = HttpMethods.POST, data = Some(data))

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[KeysResponse], Duration.Inf)
  }

  def addRelationship(clientId: UUID, relationshipId: UUID): Client = {
    val requestBody = s"""{"relationshipId": "${relationshipId.toString}"}"""

    val response = request(
      uri = s"$serviceURL/clients/${clientId.toString}/relationships",
      method = HttpMethods.POST,
      data = Some(requestBody)
    )

    response.status shouldBe StatusCodes.Created

    Await.result(Unmarshal(response).to[Client], Duration.Inf)
  }

  def request(uri: String, method: HttpMethod, data: Option[String] = None): HttpResponse = {
    val httpRequest: HttpRequest = HttpRequest(uri = uri, method = method, headers = authorization)

    val requestWithEntity: HttpRequest =
      data.fold(httpRequest)(d => httpRequest.withEntity(HttpEntity(ContentTypes.`application/json`, d)))

    Await.result(Http()(classicSystem).singleRequest(requestWithEntity), Duration.Inf)
  }
}
