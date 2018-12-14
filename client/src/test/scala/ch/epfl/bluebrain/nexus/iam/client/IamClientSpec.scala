package ch.epfl.bluebrain.nexus.iam.client

import java.time.{Clock, Instant, ZoneId}

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
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
    with IdiomaticMockitoFixture {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 200 milliseconds)

  implicit val ec: ExecutionContext = system.dispatcher

  private val clock        = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private val config       = IamClientConfig("v1", url"http://example.com/some/".value)
  private val aclsClient   = mock[HttpClient[Future, AccessControlLists]]
  private val callerClient = mock[HttpClient[Future, Caller]]
  private val client       = new IamClient[Future](config, aclsClient, callerClient)

  before {
    Mockito.reset(aclsClient)
    Mockito.reset(callerClient)
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
        client.getAcls("a" / "b", ancestors = true, self = true).futureValue shouldEqual expected
        client.authorizeOn("a" / "b", Permission.unsafe("read")).futureValue shouldEqual (())
        client.authorizeOn("a" / "b", Permission.unsafe("write")).failed.futureValue shouldEqual UnauthorizedAccess
      }

      "succeed without token" in {
        implicit val tokenOpt: Option[AuthToken] = None
        val expected                             = AccessControlLists(/ -> aclWithMeta)

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn Future(expected)
        client.getAcls("a" / "b", ancestors = true, self = true).futureValue shouldEqual expected
        client.authorizeOn("a" / "b", Permission.unsafe("read")).futureValue shouldEqual (())
      }

      "fail with UnauthorizedAccess" in {
        implicit val tokenOpt: Option[AuthToken] = None

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=true&self=true")) shouldReturn
          Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized)))
        client.getAcls("a" / "b", ancestors = true, self = true).failed.futureValue shouldEqual UnauthorizedAccess
        client.authorizeOn("a" / "b", Permission.unsafe("read")).failed.futureValue shouldEqual UnauthorizedAccess
      }

      "fail with other error" in {
        implicit val tokenOpt: Option[AuthToken] = None
        val expected                             = new RuntimeException()

        aclsClient(Get("http://example.com/some/v1/acls/a/b?ancestors=false&self=true")) shouldReturn
          Future.failed(expected)
        client.getAcls("a" / "b", ancestors = false, self = true).failed.futureValue shouldEqual expected
      }
    }

    "fetching caller" should {

      "succeed with token" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val user              = User("mysubject", "myrealm")
        val expected          = Caller(user, Set(user, Anonymous))

        callerClient(Get("http://example.com/some/v1/oauth2/user").addCredentials(token)) shouldReturn
          Future(expected)
        client.getCaller.futureValue shouldEqual expected
      }

      "succeed without token" in {
        implicit val tokenOpt = None
        client.getCaller.futureValue shouldEqual Caller.anonymous
      }

      "fail with UnauthorizedAccess" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")

        callerClient(Get("http://example.com/some/v1/oauth2/user").addCredentials(token)) shouldReturn
          Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized)))
        client.getCaller.failed.futureValue shouldEqual UnauthorizedAccess
      }

      "fail with other error" in {
        implicit val tokenOpt = Option(AuthToken("token"))
        val token             = OAuth2BearerToken("token")
        val expected          = new RuntimeException()

        callerClient(Get("http://example.com/some/v1/oauth2/user").addCredentials(token)) shouldReturn
          Future.failed(expected)
        client.getCaller.failed.futureValue shouldEqual expected
      }
    }
  }
}
