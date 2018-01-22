package ch.epfl.bluebrain.nexus.iam.oidc.routes

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{Location, OAuth2BearerToken}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
import ch.epfl.bluebrain.nexus.iam.oidc.api.Fault._
import ch.epfl.bluebrain.nexus.iam.oidc.api.Rejection.IllegalRedirectUri
import ch.epfl.bluebrain.nexus.iam.oidc.api.{IdAccessToken, OidcOps}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

//noinspection TypeAnnotation
class AuthRoutesSpec
    extends WordSpecLike
    with ScalatestRouteTest
    with Matchers
    with MockitoSugar
    with BeforeAndAfter
    with BeforeAndAfterAll {

  implicit val mt = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val op = mock[OidcOps[Future]]
  implicit val tm = Timeout(3 seconds)
  implicit val cf = Configuration.default.withSnakeCaseMemberNames

  val routes = handleExceptions(ExceptionHandling.exceptionHandler) {
    handleRejections(RejectionHandling.rejectionHandler) {
      AuthRoutes(op).routes
    }
  }

  before {
    Mockito.reset(op)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "An AuthRoutes" should {
    val accessToken = "access_token"
    val iat         = IdAccessToken(accessToken, "id_token", "token_type", 1L)
    val userInfo =
      UserInfo("sub", "name", "preferredUsername", "givenName", "familyName", "email@example.com", Set.empty)

    "redirect the user to the downstream provider" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.successful(Uri("http://localhost/redirect")))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.Found
        header[Location] shouldEqual Some(Location("http://localhost/redirect"))
      }
    }

    "redirect the user to the downstream provider when using redirect" in {
      when(op.buildRedirectUri(Some(Uri("http://localhost/final"))))
        .thenReturn(Future.successful(Uri("http://localhost/redirect")))

      val q = Query("redirect" -> "http://localhost/final")

      Get(Uri("/oauth2/authorize").withQuery(q)) ~> routes ~> check {
        status shouldEqual StatusCodes.Found
        header[Location] shouldEqual Some(Location("http://localhost/redirect"))
      }
    }

    "provide the exchanged access token" in {
      when(op.exchangeCode("code", "state"))
        .thenReturn(Future.successful(iat -> None))

      val q = Query("code" -> "code", "state" -> "state")
      Get(Uri("/oauth2/token").withQuery(q)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Map[String, String]].get(accessToken) shouldEqual Some(accessToken)
      }
    }

    "redirect to the final uri" in {
      when(op.exchangeCode("code", "state"))
        .thenReturn(Future.successful(iat -> Some(Uri("http://localhost/final"))))
      val q = Query("code" -> "code", "state" -> "state")
      Get(Uri("/oauth2/token").withQuery(q)) ~> routes ~> check {
        status shouldEqual StatusCodes.Found
        header[Location] shouldEqual Some(
          Location(Uri("http://localhost/final").withQuery(Query("access_token" -> accessToken))))
      }
    }

    "provide the user info" in {
      when(op.getUserInfo(accessToken))
        .thenReturn(Future.successful(userInfo))
      Get("/oauth2/userinfo").addCredentials(OAuth2BearerToken(accessToken)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[UserInfo] shouldEqual userInfo
      }
    }

    "reject the call with unauthorized" in {
      when(op.getUserInfo(accessToken))
        .thenReturn(Future.failed(Unauthorized))
      Get("/oauth2/userinfo").addCredentials(OAuth2BearerToken(accessToken)) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Map[String, String]] shouldEqual Map("code"    -> "Unauthorized",
                                                        "message" -> "You are not authorized to perform this request")
      }
    }

    "reject the call with unauthorized when missing credentials" in {
      Get("/oauth2/userinfo") ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Map[String, String]] shouldEqual Map("code"    -> "Unauthorized",
                                                        "message" -> "You are not authorized to perform this request")
      }
    }

    "provide a BadRequest for Rejected faults" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.failed(Rejected(IllegalRedirectUri)))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Map[String, String]] shouldEqual Map("code"    -> "IllegalRedirectUri",
                                                        "message" -> "The provided redirect uri is invalid")
      }
    }

    "provide a GatewayTimeout for TimedOut faults" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.failed(TimedOut("reason")))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.GatewayTimeout
        responseAs[Map[String, String]] shouldEqual Map(
          "code"    -> "TimedOut",
          "message" -> "A timeout occurred while communicating with a downstream provider")
      }
    }

    "provide a InternalServerError for Unexpected faults" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.failed(Unexpected("reason", "msg")))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[Map[String, String]] shouldEqual Map("code"    -> "InternalError",
                                                        "message" -> "An internal server error occurred")
      }
    }

    "provide a BadGateway for UnsuccessfulDownstreamCall faults" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.failed(UnsuccessfulDownstreamCall("reason", new IllegalArgumentException)))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.BadGateway
        responseAs[Map[String, String]] shouldEqual Map(
          "code"    -> "UnsuccessfulDownstreamCall",
          "message" -> "A call to the downstream provider failed unexpectedly")
      }
    }

    "provide a InternalServerError for InternalFault faults" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.failed(InternalFault("reason", new IllegalArgumentException)))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.InternalServerError
        responseAs[Map[String, String]] shouldEqual Map("code"    -> "InternalError",
                                                        "message" -> "An internal server error occurred")
      }
    }

    "provide a Unauthorized for Unauthorized faults" in {
      when(op.buildRedirectUri(None))
        .thenReturn(Future.failed(Unauthorized))
      Get("/oauth2/authorize") ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Map[String, String]] shouldEqual Map("code"    -> "Unauthorized",
                                                        "message" -> "You are not authorized to perform this request")
      }
    }
  }
}
