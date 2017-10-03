package ch.epfl.bluebrain.nexus.iam.service.routes


import java.util.UUID

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.iam.core.auth.DownstreamAuthClient
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.Future

class AuthRoutesSpec extends WordSpecLike
  with Matchers
  with BeforeAndAfter
  with ScalatestRouteTest
  with MockitoSugar
  with TableDrivenPropertyChecks
  with ScalaFutures {

  private val oidcConfig = new OidcConfig(
    "http://example.com/authorize",
    "http://example.com/token",
    "http://example.com/userinfo",
  )
  val cl = mock[DownstreamAuthClient[Future]]
  val routes = AuthRoutes(oidcConfig, cl).routes

  before {
    Mockito.reset(cl)
  }

  val expectedResponse = HttpResponse(StatusCodes.ImATeapot)

  "The IAM service" should {
    "forward GET requests to authorize endpoint and return the downstream response" in {
      when(cl.forward(Get(oidcConfig.authorizeEndpoint)))
        .thenReturn(Future.successful(expectedResponse))
      Get("/oauth2/authorize") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }
  }

  "forward GET requests with redirect to authorize endpoint and return the downstream response" in {
    val query = Query("redirect" -> "https://redirect.example.com/redirect")
    val forwardedRequest = Get(oidcConfig.authorizeEndpoint.withQuery(query))

    when(cl.forward(forwardedRequest))
      .thenReturn(Future.successful(expectedResponse))
    Get(s"/oauth2/authorize?${query.toString}") ~> routes ~> check {
      response shouldBe expectedResponse
    }
  }

  "forward requests to token endpoint and return the downstream response" in {
    val query = Query(
      "code" -> UUID.randomUUID().toString,
      "state" -> UUID.randomUUID().toString
    )
    val forwardedRequest = Get(oidcConfig.tokenEndpoint.withQuery(query))

    when(cl.forward(forwardedRequest))
      .thenReturn(Future.successful(expectedResponse))
    Get(s"/oauth2/token?${query.toString}") ~> routes ~> check {
      response shouldBe expectedResponse
    }
  }

  "reject request to token endpoint without code" in {
    val query = Query(
      "state" -> UUID.randomUUID().toString
    )

    Get(s"/oauth2/token?${query.toString}") ~> routes ~> check {
      response.status shouldBe StatusCodes.BadRequest
    }
  }

  "forward requests to userinfo endpoint and return the downstream response" in {
    val authHeader = Authorization(OAuth2BearerToken(UUID.randomUUID().toString))
    val forwardedRequest = Get(oidcConfig.userinfoEndpoint)
      .withHeaders(authHeader)

    when(cl.forward(forwardedRequest))
      .thenReturn(Future.successful(expectedResponse))
    Get(s"/oauth2/userinfo").addHeader(authHeader) ~> routes ~> check {
      response shouldBe expectedResponse
    }
  }

  "reject the requests to userinfo endpoint without Authorization header" in {
    Get(s"/oauth2/userinfo") ~> routes ~> check {
      response.status shouldBe StatusCodes.Unauthorized
    }
  }

}
