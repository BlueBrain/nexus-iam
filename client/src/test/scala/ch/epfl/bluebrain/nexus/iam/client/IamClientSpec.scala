package ch.epfl.bluebrain.nexus.iam.client

import java.time.{Clock, Instant, ZoneId}

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.{Get, Put}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.HttpMessage.DiscardedEntity
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{Anonymous, User}
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.mockito.Mockito
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class IamClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter
    with IdiomaticMockitoFixture
    with Resources {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 200 milliseconds)

  implicit val ec: ExecutionContext = system.dispatcher

  private val clock             = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private val config            = IamClientConfig(url"http://example.com/some/v1".value)
  private val aclsClient        = mock[HttpClient[Future, AccessControlLists]]
  private val callerClient      = mock[HttpClient[Future, Caller]]
  private val permissionsClient = mock[HttpClient[Future, Permissions]]
  private val httpClient        = mock[UntypedHttpClient[Future]]
  private val client            = new IamClient[Future](config, aclsClient, callerClient, permissionsClient, httpClient)

  before {
    Mockito.reset(aclsClient)
    Mockito.reset(callerClient)
    Mockito.reset(permissionsClient)
    Mockito.reset(httpClient)
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

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=true&self=true").addCredentials(token)) shouldReturn
          Future(expected)
        client.acls("a" / "b", ancestors = true, self = true).futureValue shouldEqual expected
        client.authorizeOn("a" / "b", Permission.unsafe("read")).futureValue shouldEqual (())
        client.authorizeOn("a" / "b", Permission.unsafe("write")).failed.futureValue shouldEqual UnauthorizedAccess
      }

      "succeed without token" in {
        implicit val tokenOpt: Option[AuthToken] = None
        val expected                             = AccessControlLists(/ -> aclWithMeta)

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn Future(expected)
        client.acls("a" / "b", ancestors = true, self = true).futureValue shouldEqual expected
        client.authorizeOn("a" / "b", Permission.unsafe("read")).futureValue shouldEqual (())
      }

      "fail with UnauthorizedAccess" in {
        implicit val tokenOpt: Option[AuthToken] = None

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn
          Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized)))
        client.acls("a" / "b", ancestors = true, self = true).failed.futureValue shouldEqual UnauthorizedAccess
        client.authorizeOn("a" / "b", Permission.unsafe("read")).failed.futureValue shouldEqual UnauthorizedAccess
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
          val request = Put("http://example.com/some/v1/acls/my/path?rev=1", entity).addCredentials(token)

          httpClient(request) shouldReturn Future.successful(HttpResponse())
          httpClient.discardBytes(HttpEntity.Empty) shouldReturn Future.successful(
            new DiscardedEntity(Future.successful(Done)))

          client.putAcls(path, acl, Some(1L)).futureValue shouldEqual (())

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
          val request = Put("http://example.com/some/v1/acls/my/path", entity).addCredentials(token)

          httpClient(request) shouldReturn Future.successful(HttpResponse())
          httpClient.discardBytes(HttpEntity.Empty) shouldReturn Future.successful(
            new DiscardedEntity(Future.successful(Done)))

          client.putAcls(path, acl).futureValue shouldEqual (())

        }
        "fail with UnauthorizedAccess" in {
          implicit val tokenOpt = Option(AuthToken("token"))
          val token             = OAuth2BearerToken("token")

          val path       = "my" / "path"
          val permission = Permission.unsafe("test/permission")
          val identity   = User("realm", "username")

          val acl = AccessControlList(
            identity -> Set(permission)
          )

          val entity  = HttpEntity(ContentTypes.`application/json`, jsonContentOf("/acls/replace.json").noSpaces)
          val request = Put("http://example.com/some/v1/acls/my/path?rev=1", entity).addCredentials(token)

          httpClient(request) shouldReturn Future.successful(HttpResponse(StatusCodes.Unauthorized))
          httpClient.discardBytes(HttpEntity.Empty) shouldReturn Future.successful(
            new DiscardedEntity(Future.successful(Done)))

          client.putAcls(path, acl, Some(1L)).failed.futureValue shouldEqual UnauthorizedAccess
        }
      }

      "fail with other error" in {
        implicit val tokenOpt: Option[AuthToken] = None
        val expected                             = new RuntimeException()

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=false&self=true")) shouldReturn
          Future.failed(expected)
        client.acls("a" / "b", ancestors = false, self = true).failed.futureValue shouldEqual expected
      }
    }

    "fetching identities" should {

      "succeed with token" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val user              = User("mysubject", "myrealm")
        val expected          = Caller(user, Set(user, Anonymous))

        callerClient(Get("http://example.com/some/v1/identities").addCredentials(token)) shouldReturn
          Future(expected)
        client.identities.futureValue shouldEqual expected
      }

      "succeed without token" in {
        implicit val tokenOpt = None
        client.identities.futureValue shouldEqual Caller.anonymous
      }

      "fail with UnauthorizedAccess" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")

        callerClient(Get("http://example.com/some/v1/identities").addCredentials(token)) shouldReturn
          Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized)))
        client.identities.failed.futureValue shouldEqual UnauthorizedAccess
      }

      "fail with other error" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val expected          = new RuntimeException()

        callerClient(Get("http://example.com/some/v1/identities").addCredentials(token)) shouldReturn
          Future.failed(expected)
        client.identities.failed.futureValue shouldEqual expected
      }
    }

    "fetching permissions" should {
      "succeed when user is authorized" in {
        implicit val tokenOpt   = Option(AuthToken("token"))
        val token               = OAuth2BearerToken("token")
        val expectedPermissions = Set(Permission.unsafe("test/perm1"), Permission.unsafe("test/perm2"))

        permissionsClient(Get("http://example.com/some/v1/permissions").addCredentials(token)) shouldReturn
          Future.successful(Permissions(expectedPermissions))
        client.permissions.futureValue shouldEqual expectedPermissions

      }

      "fail when user is authorized" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")

        permissionsClient(Get("http://example.com/some/v1/permissions").addCredentials(token)) shouldReturn
          Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized)))
        client.permissions.failed.futureValue shouldEqual UnauthorizedAccess

      }
    }
  }
}
