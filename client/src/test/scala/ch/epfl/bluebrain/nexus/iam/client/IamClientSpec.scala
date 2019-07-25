package ch.epfl.bluebrain.nexus.iam.client

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.{Get, Put}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.testkit.TestKit
import cats.effect.IO
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.test.io.IOValues
import ch.epfl.bluebrain.nexus.iam.client.IamClientError.{Forbidden, Unauthorized}
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{Anonymous, User}
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.iam.client.types.events.Event
import ch.epfl.bluebrain.nexus.iam.client.types.events.Event._
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.Json
import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito, Mockito}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfter, EitherValues, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.util.Random

//noinspection ScalaUnnecessaryParentheses,TypeAnnotation,RedundantDefaultArgument
class IamClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfter
    with IdiomaticMockito
    with ArgumentMatchersSugar
    with IOValues
    with EitherValues
    with Resources
    with Eventually {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 15 milliseconds)

  private implicit val mt: Materializer = ActorMaterializer()
  private val clock                     = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private val config =
    IamClientConfig(url"http://example.com/some/v1".value, url"http://internal.example.com/some/v1".value)
  private val aclsClient        = mock[HttpClient[IO, AccessControlLists]]
  private val callerClient      = mock[HttpClient[IO, Caller]]
  private val permissionsClient = mock[HttpClient[IO, Permissions]]
  private val jsonClient        = mock[HttpClient[IO, Json]]
  private val source            = mock[EventSource[Event]]
  private val client            = new IamClient[IO](source, config, aclsClient, callerClient, permissionsClient, jsonClient)

  before {
    Mockito.reset(aclsClient)
    Mockito.reset(callerClient)
    Mockito.reset(permissionsClient)
    Mockito.reset(jsonClient)
    Mockito.reset(source)
  }

  "An IAM client" when {

    "fetching ACLs and authorizing" should {
      val acl = AccessControlList(Anonymous -> Set(Permission.unsafe("create"), Permission.unsafe("read")))
      val aclWithMeta = ResourceAccessControlList(url"http://example.com/id".value,
                                                  7L,
                                                  Set.empty,
                                                  clock.instant(),
                                                  Anonymous,
                                                  clock.instant(),
                                                  Anonymous,
                                                  acl)

      "succeed with token" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val expected          = AccessControlLists(/ -> aclWithMeta)

        aclsClient(Get("http://internal.example.com/some/v1/acls/a/b?ancestors=true&self=true").addCredentials(token)) shouldReturn
          IO(expected)
        client.acls("a" / "b", ancestors = true, self = true).ioValue shouldEqual expected
        client.hasPermission("a" / "b", Permission.unsafe("read")).ioValue shouldEqual true
        client.hasPermission("a" / "b", Permission.unsafe("write")).ioValue shouldEqual false
      }

      "succeed without token" in {
        implicit val tokenOpt: Option[AuthToken] = None
        val expected                             = AccessControlLists(/ -> aclWithMeta)

        aclsClient(Get("http://internal.example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn IO(
          expected)
        client.acls("a" / "b", ancestors = true, self = true).ioValue shouldEqual expected
        client.hasPermission("a" / "b", Permission.unsafe("read")).ioValue shouldEqual true
      }

      "fail with UnauthorizedAccess" in {
        implicit val tokenOpt: Option[AuthToken] = None
        aclsClient(Get("http://internal.example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn
          IO.raiseError(Unauthorized("{}"))
        client.acls("a" / "b", ancestors = true, self = true).failed[Unauthorized]
        client.hasPermission("a" / "b", Permission.unsafe("read")).failed[Unauthorized]
      }

      "fail with UnexpectedUnsuccessfulHttpResponse" in {
        implicit val tokenOpt: Option[AuthToken] = None
        aclsClient(Get("http://internal.example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn
          IO.raiseError(UnexpectedUnsuccessfulHttpResponse(HttpResponse(status = InternalServerError), "none"))
        client.acls("a" / "b", ancestors = true, self = true).failed[IamClientError.UnknownError]
        client.hasPermission("a" / "b", Permission.unsafe("read")).failed[IamClientError.UnknownError]
      }

      "fail with Forbidden" in {
        implicit val tokenOpt: Option[AuthToken] = None
        aclsClient(Get("http://internal.example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn
          IO.raiseError(Forbidden("{}"))
        client.acls("a" / "b", ancestors = true, self = true).failed[Forbidden]
        client.hasPermission("a" / "b", Permission.unsafe("read")).failed[Forbidden]
      }

      "replacing ACLs" should {
        "succeed with token" in {
          implicit val tokenOpt = Option(AuthToken("token"))
          val token             = OAuth2BearerToken("token")

          val path       = "my" / "path"
          val permission = Permission.unsafe("test/permission")
          val identity   = User("realm", "username")

          val acl = AccessControlList(
            identity -> Set(permission)
          )

          val entity  = HttpEntity(ContentTypes.`application/json`, jsonContentOf("/acls/replace.json").noSpaces)
          val request = Put("http://internal.example.com/some/v1/acls/my/path?rev=1", entity).addCredentials(token)

          jsonClient(request) shouldReturn IO.pure(Json.obj())
          client.putAcls(path, acl, Some(1L)).ioValue shouldEqual (())
        }

        "succeed with token without rev" in {
          implicit val tokenOpt = Option(AuthToken("token"))
          val token             = OAuth2BearerToken("token")

          val path       = "my" / "path"
          val permission = Permission.unsafe("test/permission")
          val identity   = User("realm", "username")

          val acl = AccessControlList(
            identity -> Set(permission)
          )

          val entity  = HttpEntity(ContentTypes.`application/json`, jsonContentOf("/acls/replace.json").noSpaces)
          val request = Put("http://internal.example.com/some/v1/acls/my/path", entity).addCredentials(token)

          jsonClient(request) shouldReturn IO.pure(Json.obj())
          client.putAcls(path, acl).ioValue shouldEqual (())
        }
        "fail with Unauthorized" in {
          implicit val tokenOpt = Option(AuthToken("token"))
          val token             = OAuth2BearerToken("token")

          val path       = "my" / "path"
          val permission = Permission.unsafe("test/permission")
          val identity   = User("realm", "username")

          val acl = AccessControlList(
            identity -> Set(permission)
          )

          val entity  = HttpEntity(ContentTypes.`application/json`, jsonContentOf("/acls/replace.json").noSpaces)
          val request = Put("http://internal.example.com/some/v1/acls/my/path?rev=1", entity).addCredentials(token)

          jsonClient(request) shouldReturn IO.raiseError(Unauthorized("{}"))
          client.putAcls(path, acl, Some(1L)).failed[Unauthorized]
        }
      }

      "fail with other error" in {
        implicit val tokenOpt: Option[AuthToken] = None
        val expected                             = new RuntimeException()

        aclsClient(Get("http://internal.example.com/some/v1/acls/a/b?ancestors=false&self=true")) shouldReturn
          IO.raiseError(expected)
        client.acls("a" / "b", ancestors = false, self = true).failed[RuntimeException] shouldEqual expected
      }
    }

    "fetching identities" should {

      "succeed with token" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val user              = User("mysubject", "myrealm")
        val expected          = Caller(user, Set(user, Anonymous))

        callerClient(Get("http://internal.example.com/some/v1/identities").addCredentials(token)) shouldReturn IO.pure(
          expected)
        client.identities.ioValue shouldEqual expected
      }

      "succeed without token" in {
        implicit val tokenOpt = None
        client.identities.ioValue shouldEqual Caller.anonymous
      }

      "fail with UnauthorizedAccess" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")

        callerClient(Get("http://internal.example.com/some/v1/identities").addCredentials(token)) shouldReturn
          IO.raiseError(Unauthorized("{}"))
        client.identities.failed[Unauthorized]
      }

      "fail with Forbidden" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")

        callerClient(Get("http://internal.example.com/some/v1/identities").addCredentials(token)) shouldReturn
          IO.raiseError(Forbidden("{}"))
        client.identities.failed[Forbidden]
      }

      "fail with other error" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val expected          = new RuntimeException()

        callerClient(Get("http://internal.example.com/some/v1/identities").addCredentials(token)) shouldReturn
          IO.raiseError(expected)
        client.identities.failed[RuntimeException] shouldEqual expected
      }
    }

    "fetching permissions" should {
      "succeed when user is authorized" in {
        implicit val tokenOpt   = Option(AuthToken("token"))
        val token               = OAuth2BearerToken("token")
        val expectedPermissions = Set(Permission.unsafe("test/perm1"), Permission.unsafe("test/perm2"))

        permissionsClient(Get("http://internal.example.com/some/v1/permissions").addCredentials(token)) shouldReturn
          IO.pure(Permissions(expectedPermissions))
        client.permissions.ioValue shouldEqual expectedPermissions

      }

      "fail when user is not authorized" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        permissionsClient(Get("http://internal.example.com/some/v1/permissions").addCredentials(token)) shouldReturn
          IO.raiseError(Unauthorized("{}"))
        client.permissions.failed[Unauthorized]
      }

      "fail when user is forbidden" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        permissionsClient(Get("http://internal.example.com/some/v1/permissions").addCredentials(token)) shouldReturn
          IO.raiseError(Forbidden("{}"))
        client.permissions.failed[Forbidden]
      }
    }

    "reading from the events SSE" should {
      implicit val token: Option[AuthToken] = None

      abstract class Ctx {
        val count = new AtomicInteger()
        val resources = List(
          "/events/acl-replaced.json",
          "/events/acl-appended.json",
          "/events/acl-subtracted.json",
          "/events/acl-deleted.json",
          "/events/permissions-replaced.json",
          "/events/permissions-appended.json",
          "/events/permissions-subtracted.json",
          "/events/permissions-deleted.json",
          "/events/realm-created.json",
          "/events/realm-updated.json",
          "/events/realm-deprecated.json"
        )

        val eventsSource = Source(Random.shuffle(resources).map(jsonContentOf(_).as[Event].right.value))
      }

      "apply function when new acl event is received" in new Ctx {
        val f: AclEvent => IO[Unit] = {
          case _: AclReplaced   => IO(count.addAndGet(1)) *> IO.unit
          case _: AclAppended   => IO(count.addAndGet(2)) *> IO.unit
          case _: AclSubtracted => IO(count.addAndGet(3)) *> IO.unit
          case _: AclDeleted    => IO(count.addAndGet(4)) *> IO.unit
        }
        val eventsIri = Iri.url("http://internal.example.com/some/v1/acls/events").right.value
        source(eventsIri, None) shouldReturn eventsSource
        client.aclEvents(f)
        eventually(count.get() shouldEqual 10)
      }

      "apply function when new permissions event is received" in new Ctx {
        val f: PermissionsEvent => IO[Unit] = {
          case _: PermissionsReplaced   => IO(count.addAndGet(1)) *> IO.unit
          case _: PermissionsAppended   => IO(count.addAndGet(2)) *> IO.unit
          case _: PermissionsSubtracted => IO(count.addAndGet(3)) *> IO.unit
          case _: PermissionsDeleted    => IO(count.addAndGet(4)) *> IO.unit
        }
        val eventsIri = Iri.url("http://internal.example.com/some/v1/permissions/events").right.value
        source(eventsIri, None) shouldReturn eventsSource
        client.permissionEvents(f)
        eventually(count.get() shouldEqual 10)
      }

      "apply function when new realms event is received" in new Ctx {
        val f: RealmEvent => IO[Unit] = {
          case _: RealmCreated    => IO(count.addAndGet(1)) *> IO.unit
          case _: RealmUpdated    => IO(count.addAndGet(2)) *> IO.unit
          case _: RealmDeprecated => IO(count.addAndGet(3)) *> IO.unit
        }
        val eventsIri = Iri.url("http://internal.example.com/some/v1/realms/events").right.value
        source(eventsIri, None) shouldReturn eventsSource
        client.realmEvents(f)
        eventually(count.get() shouldEqual 6)
      }
    }
  }
}
