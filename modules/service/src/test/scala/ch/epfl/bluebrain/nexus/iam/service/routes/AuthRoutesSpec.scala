package ch.epfl.bluebrain.nexus.iam.service.routes

import java.util.UUID

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse
import ch.epfl.bluebrain.nexus.iam.core.auth.UserInfo
import ch.epfl.bluebrain.nexus.iam.service.auth.DownstreamAuthClient
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import scala.concurrent.Future

class AuthRoutesSpec
    extends WordSpecLike
    with Matchers
    with BeforeAndAfter
    with ScalatestRouteTest
    with MockitoSugar
    with ScalaFutures {

  val cl     = mock[DownstreamAuthClient[Future]]
  val routes = AuthRoutes(cl).routes

  before {
    Mockito.reset(cl)
  }

  val expectedResponse = HttpResponse(StatusCodes.ImATeapot)

  "The IAM service" should {
    "forward GET requests to authorize endpoint and return the downstream response" in {
      when(cl.authorize(None)).thenReturn(Future.successful(expectedResponse))
      Get("/oauth2/authorize") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }

    "forward GET requests with redirect to authorize endpoint and return the downstream response" in {
      val callback = "https://redirect.example.com/redirect"
      when(cl.authorize(Some(callback))).thenReturn(Future.successful(expectedResponse))
      Get(s"/oauth2/authorize?redirect=$callback") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }

    "forward requests to token endpoint and return the downstream response" in {
      val code  = UUID.randomUUID.toString
      val state = UUID.randomUUID.toString

      when(cl.token(code, state)).thenReturn(Future.successful(expectedResponse))
      Get(s"/oauth2/token?${Query("code" -> code, "state" -> state).toString}") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }

    "reject request to token endpoint without code" in {
      Get(s"/oauth2/token?state=foo") ~> routes ~> check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }
    "reject request to token endpoint without state" in {
      Get(s"/oauth2/token?code=bar") ~> routes ~> check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }

    "forward requests to userinfo endpoint and return the downstream response" in {
      val credentials = OAuth2BearerToken(UUID.randomUUID.toString)
      val alice =
        UserInfo("alice", "Alice Smith", "alice", "Alice", "Smith", "alice.smith@example.com", Set("some-group"))

      when(cl.userInfo(credentials)).thenReturn(Future.successful(alice))
      Get(s"/oauth2/userinfo") ~> addCredentials(credentials) ~> routes ~> check {
        responseAs[UserInfo] shouldBe alice
      }
      when(cl.userInfo(credentials))
        .thenReturn(Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized))))
      Get(s"/oauth2/userinfo") ~> addCredentials(credentials) ~> routes ~> check {
        response.status shouldBe StatusCodes.Unauthorized
      }
    }

    "reject the requests to userinfo endpoint without proper OAuth 2.0 Authorization header" in {
      Get(s"/oauth2/userinfo") ~> routes ~> check {
        response.status shouldBe StatusCodes.Unauthorized
      }
      Get(s"/oauth2/userinfo") ~> addCredentials(BasicHttpCredentials("user", "pass")) ~> routes ~> check {
        response.status shouldBe StatusCodes.Unauthorized
      }
    }
  }
}
