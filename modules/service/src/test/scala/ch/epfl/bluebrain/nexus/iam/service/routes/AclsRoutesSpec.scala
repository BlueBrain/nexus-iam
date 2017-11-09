package ch.epfl.bluebrain.nexus.iam.service.routes

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.TimeUnit

import akka.cluster.Cluster
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.ActorMaterializer
import akka.testkit.TestDuration
import akka.util.Timeout
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls._
import ch.epfl.bluebrain.nexus.commons.iam.auth.{AuthenticatedUser, UserInfo}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.State.Initial
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.service.auth.{DownstreamAuthClient, TokenId}
import ch.epfl.bluebrain.nexus.iam.service.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejection._
import ch.epfl.bluebrain.nexus.iam.service.routes.Error.classNameOf
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future, Promise}
import scala.util.Random
import akka.http.scaladsl.model.ContentTypes._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.{JsonLdSerialization, SimpleIdentitySerialization}
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.{Decoder, Encoder, Json}

class AclsRoutesSpec extends AclsRoutesSpecInstances {

  "The ACL service" should {
    "reject unauthorized requests" in {
      val path = Path(s"/some/$rand")
      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"@type": "Anonymous"}, "permissions": ["own", "read", "write"] }, {"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["read", "write"] } ] }"""
        )
      ) ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Delete(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Get(s"/acls${path.repr}?all=true") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Get(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "reject command with invalid payload" in {
      val path    = Path(s"/some/$rand")
      val content = HttpEntity(`application/json`, """{"foo": ["bar"]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[WrongOrInvalidJson.type]
      }
    }

    "reject command with invalid permissions" in {
      val path = Path(s"/some/$rand")
      val content =
        HttpEntity(`application/json`, """{"acl":[{"permissions": ["random123"], "identity": {}}]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalPermissionString.type]
      }
    }

    "reject command with invalid identity" in {
      val path = Path(s"/some/$rand")
      val content =
        HttpEntity(`application/json`, """{"acl":[{"permissions": ["read"], "identity": {"realm": "foö://bar"}}]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalIdentityFormat.type]
      }
    }

    "reject requesting invalid HTTP verb" in {
      val path = Path(s"/some/$rand")
      Options(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].code shouldEqual classNameOf[MethodNotSupported.type]
      }
    }

    "reject clearing nonexistent permissions" in {
      val path = Path(s"/some/$rand")
      Delete(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[CannotClearNonexistentPermissions.type]
      }
    }

    "reject creating empty permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}",
          HttpEntity(
            `application/json`,
            """{"acl" : [{"identity" : {"@type" : "Anonymous"}, "permissions" : [] } ] }""")) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotCreateVoidPermissions.type]
      }
    }

    "reject adding empty permissions" in {
      val path = Path(s"/some/$rand")
      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["own", "read", "write"] } ] }"""
        )
      ) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Post(
        s"/acls${path.repr}",
        HttpEntity(`application/json`, """{"identity" : {"@type" : "Anonymous"}, "permissions" : [] }""")) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotAddVoidPermissions.type]
      }
    }

    "clear permissions" in {
      val path = Path(s"/some/$rand")
      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"@type": "Anonymous"}, "permissions": ["own", "read", "write"] }, {"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["read", "write"] } ] }"""
        )
      ) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Delete(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
      Get(s"/acls${path.repr}?all=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList].acl shouldBe empty
      }
      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        println(responseAs[Json])
        responseAs[AccessControlList] shouldEqual AccessControlList(alice -> own)
      }
    }

    "create and get permissions" in {
      val path = Path(s"/some/$rand")
      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"realm": "realm", "group": "some", "@type": "GroupRef"}, "permissions": ["own", "read", "write"] }, {"identity": {"realm": "realm", "group": "other-group", "@type": "GroupRef"}, "permissions": ["own", "read", "write"] }, {"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["read", "write"] }, {"identity": {"@type": "Anonymous"}, "permissions": ["read"] } ] }"""
        )
      ) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Get(s"/acls${path.repr}?all=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(
          someGroup   -> ownReadWrite,
          otherGroup  -> ownReadWrite,
          alice       -> readWrite,
          Anonymous() -> read
        )
      }
      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(
          someGroup   -> ownReadWrite,
          alice       -> ownReadWrite,
          Anonymous() -> read
        )
      }
    }

    "add permissions" in {
      val path = Path(s"/some/$rand")
      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["read", "write"] } ] }"""
        )
      ) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(alice -> ownReadWrite)
      }

      Post(s"/acls${path.repr}",
           HttpEntity(`application/json`,
                      """{"identity": {"@type": "Anonymous"}, "permissions": ["read", "write"] }""")) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(Anonymous(), readWrite)
      }
      Get(s"/acls${path.repr}?all=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(Anonymous() -> readWrite, alice -> readWrite)
      }
    }

    "handle downstream error codes" in {
      when(ucl.apply(Get(provider.userinfoEndpoint).addCredentials(credentialsNoUser)))
        .thenReturn(Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized))))

      Get("/acls/a/b/c") ~> addCredentials(credentialsNoUser) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }
      when(ucl.apply(Get(provider.userinfoEndpoint).addCredentials(credentialsNoUser)))
        .thenReturn(Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.BadGateway))))

      Get("/acls/a/b/c") ~> addCredentials(credentialsNoUser) ~> routes ~> check {
        status shouldEqual StatusCodes.BadGateway
      }

      when(ucl.apply(Get(provider.userinfoEndpoint).addCredentials(credentialsNoUser)))
        .thenReturn(Future.failed(new RuntimeException()))

      Get("/acls/a/b/c") ~> addCredentials(credentialsNoUser) ~> routes ~> check {
        status shouldEqual StatusCodes.InternalServerError
      }
    }

  }
}

abstract class AclsRoutesSpecInstances
    extends WordSpecLike
    with Matchers
    with Eventually
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with MockitoSugar
    with Fixtures {
  private val appConfig                        = Settings(system).appConfig
  private implicit val clock                   = Clock.fixed(Instant.ofEpochMilli(1), ZoneId.systemDefault())
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 50 millis)
  implicit val tm: Timeout                     = Timeout(appConfig.runtime.defaultTimeout.toMillis, TimeUnit.MILLISECONDS)
  implicit val rt: RouteTestTimeout            = RouteTestTimeout(5.seconds.dilated)
  implicit val config: Configuration           = Configuration.default.withDiscriminator("type")

  protected val ownReadWrite = Permissions(Own, Read, Write)
  protected val readWrite    = Permissions(Read, Write)
  protected val own          = Permissions(Own)
  protected val read         = Permissions(Read)
  protected val realm        = "realm"
  protected val alice        = UserRef(realm, "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero")
  protected val aliceCaller  = CallerCtx(clock, AuthenticatedUser(Set(alice)))
  protected val someGroup    = GroupRef(realm, "some")
  protected val otherGroup   = GroupRef(realm, "other-group")

  implicit val ec: ExecutionContextExecutor  = system.dispatcher
  implicit val ucl                           = mock[UntypedHttpClient[Future]]
  implicit val mt: ActorMaterializer         = ActorMaterializer()
  val uicl                                   = HttpClient.withAkkaUnmarshaller[UserInfo]
  val provider: AppConfig.OidcProviderConfig = oidc.providers(0)
  val cl                                     = List[DownstreamAuthClient[Future]](DownstreamAuthClient(ucl, uicl, provider))
  implicit val claimExtractor                = claim(cl)
  implicit val apiUri: ApiUri                = ApiUri("localhost:8080/v0")
  protected val credentials                  = genCredentials(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)
  protected val credentialsNoUser =
    genCredentailsNoUserInfo(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)

  protected val user = AuthenticatedUser(Set(Anonymous(), AuthenticatedRef(Some(realm)), someGroup, alice))
  implicit val enc: Encoder[Identity] =
    JsonLdSerialization.identityEncoder(apiUri.base.copy(path = apiUri.base.path / "realms"))
  implicit val dec: Decoder[Identity] = SimpleIdentitySerialization.identityDecoder

  var routes: Route = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val p       = Promise[Unit]()
    val cluster = Cluster(system)
    cluster.registerOnMemberUp {
      val aggregate =
        ShardingAggregate("permission", SourcingAkkaSettings(journalPluginId = "inmemory-read-journal"))(Initial,
                                                                                                         Acls.next,
                                                                                                         Acls.eval)
      val acl = Acls[Future](aggregate)
      acl.create(Path./, AccessControlList(alice -> own))(aliceCaller)
      routes = AclsRoutes(acl).routes
      p.success(())
    }
    cluster.join(cluster.selfAddress)
    Await.result(p.future, rt.duration)
  }

  def rand: String = Math.abs(Random.nextLong).toString

}
