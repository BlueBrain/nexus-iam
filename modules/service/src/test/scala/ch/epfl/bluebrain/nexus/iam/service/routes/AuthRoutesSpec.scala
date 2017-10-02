package ch.epfl.bluebrain.nexus.iam.service.routes


import java.util.UUID

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, Location, OAuth2BearerToken}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.Future

class AuthRoutesSpec  extends WordSpecLike
  with Matchers
  with BeforeAndAfter
  with ScalatestRouteTest
  with MockitoSugar
  with TableDrivenPropertyChecks {

  private val oidcConfig = new OidcConfig(
      "http://example.com/authorize",
      "http://example.com/token",
      "http://example.com/userinfo",
  )
  val cl = mock[UntypedHttpClient[Future]]
  val routes  = AuthRoutes(oidcConfig)(cl).routes

  before {
    Mockito.reset(cl)
  }

  "The IAM service" should {
    val testResponses = Table(
      "response",
      HttpResponse(StatusCodes.OK, entity ="""{"response": "OK"}"""),
      HttpResponse(StatusCodes.Found).addHeader(Location(Uri("https://example.com/redirect"))),
      HttpResponse(StatusCodes.BadGateway),
      HttpResponse(StatusCodes.GatewayTimeout)
    )
    "forward GET requests to authorize endpoint and return the downstream response" in {
      forAll(testResponses) { expectedResp =>
        when(cl.apply(HttpRequest(HttpMethods.GET, oidcConfig.authorizeEndpoint)))
            .thenReturn(Future.successful(expectedResp))
        Get("/oauth2/authorize") ~> routes ~> check {
          response shouldBe expectedResp
        }
      }
    }

    "forward GET requests with redirect to authorize endpoint and return the downstream response" in {
      val query = Query("redirect" -> "https://redirect.example.com/redirect")
      val forwardedRequest = HttpRequest(HttpMethods.GET, oidcConfig.authorizeEndpoint.withQuery(query))

      forAll(testResponses) { expectedResp =>
        when(cl.apply(forwardedRequest))
          .thenReturn(Future.successful(expectedResp))
        Get(s"/oauth2/authorize?${query.toString}") ~> routes ~> check {
          response shouldBe expectedResp
        }
      }
    }

    "forward requests to token endpoint" in {
      val query = Query(
          "code" -> UUID.randomUUID().toString,
          "state" -> UUID.randomUUID().toString
        )
      val forwardedRequest = HttpRequest(HttpMethods.GET, oidcConfig.tokenEndpoint.withQuery(query))

      forAll(testResponses) { expectedResp =>
        when(cl.apply(forwardedRequest))
          .thenReturn(Future.successful(expectedResp))
        Get(s"/oauth2/token?${query.toString}") ~> routes ~> check {
          response shouldBe expectedResp
        }
      }
    }

    "forward requests to userinfo endpoint" in {
      val authHeader = Authorization(OAuth2BearerToken(UUID.randomUUID().toString))
      val forwardedRequest = HttpRequest(HttpMethods.GET, oidcConfig.userinfoEndpoint)
        .withHeaders(authHeader)

      forAll(testResponses) { expectedResp =>
        when(cl.apply(forwardedRequest))
          .thenReturn(Future.successful(expectedResp))
        Get(s"/oauth2/userinfo").addHeader(authHeader) ~> routes ~> check {
          response shouldBe expectedResp
        }
      }
    }
  }

}
