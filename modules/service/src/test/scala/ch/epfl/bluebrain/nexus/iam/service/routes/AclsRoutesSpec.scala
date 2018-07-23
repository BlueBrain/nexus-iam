package ch.epfl.bluebrain.nexus.iam.service.routes

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import akka.cluster.Cluster
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.ActorMaterializer
import akka.testkit.TestDuration
import akka.util.Timeout
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.{OrderedKeys, unmarshaller}
import ch.epfl.bluebrain.nexus.commons.http.{ContextUri, HttpClient, RdfMediaTypes, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.State.Initial
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.types._
import ch.epfl.bluebrain.nexus.iam.core.groups.UsedGroups
import ch.epfl.bluebrain.nexus.iam.core.{AuthenticatedUser, ServiceAccount, User}
import ch.epfl.bluebrain.nexus.iam.elastic.SimpleIdentitySerialization
import ch.epfl.bluebrain.nexus.iam.elastic.query.FilterAcls
import ch.epfl.bluebrain.nexus.iam.elastic.types.FullAccessControlList
import ch.epfl.bluebrain.nexus.iam.service.Main
import ch.epfl.bluebrain.nexus.iam.service.auth.{DownstreamAuthClient, TokenId}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.ContextConfig
import ch.epfl.bluebrain.nexus.iam.service.config.Settings
import ch.epfl.bluebrain.nexus.iam.service.io.JsonLdSerialization
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejection._
import ch.epfl.bluebrain.nexus.iam.service.routes.Error.classNameOf
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.mockito.ArgumentMatchers.{isA, eq => mEq}
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random

class AclsRoutesSpec extends AclsRoutesSpecInstances with Resources {

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
    }

    "reject command with invalid payload" in {
      val path    = Path(s"/some/$rand")
      val content = HttpEntity(`application/json`, """{"foo": ["bar"]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[WrongOrInvalidJson.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
      }
    }

    "reject command with invalid permissions" in {
      val path = Path(s"/some/$rand")
      val content =
        HttpEntity(`application/json`, """{"acl":[{"permissions": ["random123"], "identity": {}}]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalPermissionString.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
      }
    }

    "reject command with invalid identity" in {
      val path = Path(s"/some/$rand")
      val content =
        HttpEntity(`application/json`, """{"acl":[{"permissions": ["read"], "identity": {"realm": "foö://bar"}}]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalIdentityFormat.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
      }
    }

    "reject requesting invalid HTTP verb" in {
      val path = Path(s"/some/$rand")
      Options(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].code shouldEqual classNameOf[MethodNotSupported.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
      }
    }

    "reject clearing nonexistent permissions" in {
      val path = Path(s"/some/$rand")
      Delete(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[CannotClearNonexistentPermissions.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
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
        responseAs[Error].code shouldEqual classNameOf[CannotAddVoidPermissions.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
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
        status shouldEqual StatusCodes.OK
      }
      Put(s"/acls${path.repr}",
          HttpEntity(`application/json`,
                     """{"acl": [{"identity" : {"@type" : "Anonymous"}, "permissions" : [] } ] }""")) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotAddVoidPermissions.type]
        responseAs[Error].`@context` shouldEqual contexts.error.toString
        contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
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
        status shouldEqual StatusCodes.OK
      }
      Delete(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }

    "subtract permissions" in {
      val path    = Path(s"/some/$rand")
      val publish = Permission("publish")

      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"@type": "Anonymous"}, "permissions": ["own", "read", "write"] }, {"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["read", "write", "publish"] } ] }"""
        )
      ) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }

      Patch(s"/acls${path.repr}", HttpEntity(`application/json`, contentOf("/patch/subtract_1.json"))) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(alice, Permissions(Write, publish))
      }

      Patch(s"/acls${path.repr}", HttpEntity(`application/json`, contentOf("/patch/subtract_3.json"))) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(alice, Permissions.empty)
      }
    }

    "add initial and get permissions" in {
      val path = Path(s"/some/$rand")
      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"realm": "realm", "group": "some", "@type": "GroupRef"}, "permissions": ["own", "read", "write", "projects/read"] }, {"identity": {"realm": "realm", "group": "other-group", "@type": "GroupRef"}, "permissions": ["own", "read", "write"] }, {"identity": {"realm": "realm", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "@type": "UserRef"}, "permissions": ["read", "write"] }, {"identity": {"@type": "Anonymous"}, "permissions": ["read"] } ] }"""
        )
      ) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseEntity shouldEqual HttpEntity.Empty
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
        status shouldEqual StatusCodes.OK
      }

      Put(
        s"/acls${path.repr}",
        HttpEntity(
          `application/json`,
          """{"acl": [{"identity": {"@type": "Anonymous"}, "permissions": ["read", "write"] } ] }""")) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "get permissions with a filtered path" in {
      val path = Path(s"/some/*/$rand")
      val acls =
        FullAccessControlList((Anonymous(), path, read), (alice, path, readWrite), (alice, path / "two", ownReadWrite))
      when(filter(mEq(path), parents = mEq(false), self = mEq(false))(isA(classOf[User])))
        .thenReturn(Future.successful(acls))

      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val expected = jsonContentOf("/identities-acls.json",
                                     Map(Pattern.quote("{{path1}}") -> s"$path",
                                         Pattern.quote("{{path2}}") -> (path / "two").toString))
        responseAs[Json] shouldEqual expected
      }
    }

    "get permissions with a filtered path and self = true and parents = true" in {
      val path = Path(s"/some/*/$rand/*")
      val acls =
        FullAccessControlList((Anonymous(), path, read), (alice, path, readWrite), (alice, path / "two", ownReadWrite))
      when(filter(mEq(path), parents = mEq(true), self = mEq(true))(isA(classOf[User])))
        .thenReturn(Future.successful(acls))

      Get(s"/acls${path.repr}?self=true&parents=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val expected = jsonContentOf("/identities-acls.json",
                                     Map(Pattern.quote("{{path1}}") -> s"$path",
                                         Pattern.quote("{{path2}}") -> (path / "two").toString))
        responseAs[Json] shouldEqual expected
      }
    }

    "get permissions from a service account" in {
      val path = Path(s"/some/*/$rand")
      val acls =
        FullAccessControlList((Anonymous(), path, read), (alice, path, readWrite), (alice, path / "two", ownReadWrite))
      when(filter(mEq(path), parents = mEq(false), self = mEq(false))(mEq(ServiceAccount)))
        .thenReturn(Future.successful(acls))

      Get(s"/acls${path.repr}") ~> addCredentials(serviceCredentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val expected = jsonContentOf("/identities-acls.json",
                                     Map(Pattern.quote("{{path1}}") -> s"$path",
                                         Pattern.quote("{{path2}}") -> (path / "two").toString))
        responseAs[Json] shouldEqual expected
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
  implicit val config: Configuration           = Configuration.default.withDiscriminator("@type")

  protected val ownReadWrite = Permissions(Own, Read, Write)
  protected val readWrite    = Permissions(Read, Write)
  protected val own          = Permissions(Own)
  protected val read         = Permissions(Read)
  protected val realm        = "realm"
  protected val serviceRealm = "service-realm"
  protected val alice        = UserRef(realm, "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero")
  protected val aliceCaller  = CallerCtx(clock, AuthenticatedUser(Set(alice)))
  protected val someGroup    = GroupRef(realm, "some")
  protected val otherGroup   = GroupRef(realm, "other-group")

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val ucl                          = mock[UntypedHttpClient[Future]]
  implicit val mt: ActorMaterializer        = ActorMaterializer()
  implicit val tracing: TracingDirectives   = TracingDirectives()
  val uicl                                  = HttpClient.withAkkaUnmarshaller[UserInfo]
  val provider                              = oidc.providers.head
  val cl                                    = oidc.providers.map(provider => DownstreamAuthClient[Future](ucl, uicl, provider))
  implicit val claimExtractor               = claim(cl)
  implicit val apiUri: ApiUri               = ApiUri("localhost:8080/v0")
  implicit val contexts = ContextConfig(ContextUri("http://localhost:8080/v0/contexts/nexus/core/error/v0.1.0"),
                                        ContextUri("http://localhost:8080/v0/contexts/nexus/core/iam/v0.1.0"))
  protected val credentials = genCredentials(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)
  protected val credentialsNoUser =
    genCredentailsNoUserInfo(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)

  protected val user                  = AuthenticatedUser(Set(Anonymous(), AuthenticatedRef(Some(realm)), someGroup, alice))
  implicit val enc: Encoder[Identity] = JsonLdSerialization.identityEncoder(apiUri.base)
  implicit val dec: Decoder[Identity] = SimpleIdentitySerialization.identityDecoder
  implicit val ordered: OrderedKeys   = Main.iamOrderedKeys
  val filter                          = mock[FilterAcls[Future]]

  val aggregate  = MemoryAggregate("used-groups")(Set.empty[GroupRef], UsedGroups.next, UsedGroups.eval).toF[Future]
  val usedGroups = UsedGroups[Future](aggregate)

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
      acl.add(Path./, AccessControlList(alice -> own))(aliceCaller)
      routes = AclsRoutes(acl, filter, usedGroups, serviceRealm).routes
      p.success(())
    }
    cluster.join(cluster.selfAddress)
    Await.result(p.future, rt.duration)
  }
  def rand: String = Math.abs(Random.nextLong).toString

}
