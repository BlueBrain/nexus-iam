package ch.epfl.bluebrain.nexus.iam.service.auth

import java.util.UUID

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.core.auth.UserInfo
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DownstreamAuthClientSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfter
    with TableDrivenPropertyChecks
    with ScalaFutures {

  private val oidc = OidcConfig(
    "http://example.com/realm",
    "http://example.com/authorize",
    "http://example.com/token",
    "http://example.com/userinfo",
  )
  implicit val cl    = mock[UntypedHttpClient[Future]]
  implicit val uicl  = mock[HttpClient[Future, UserInfo]]
  private val client = DownstreamAuthClient(oidc, cl, uicl)

  before {
    Mockito.reset(cl)
  }

  "DownstreamAuthClient" should {
    val successResponses = Table(
      "response",
      HttpResponse(StatusCodes.OK, entity = """{"response": "OK"}"""),
      HttpResponse(StatusCodes.Found).addHeader(Location(Uri("https://example.com/redirect")))
    )

    "forward requests and return successful responses" in {
      forAll(successResponses) { expectedResp =>
        val request = Get(s"http://example.com/${UUID.randomUUID().toString}")
        when(cl.apply(request))
          .thenReturn(Future.successful(expectedResp))

        client.forward(request).futureValue shouldBe expectedResp
      }
    }

    val errorResponses = Table(
      ("downstream response", "expected status code"),
      (HttpResponse(StatusCodes.BadRequest), StatusCodes.InternalServerError),
      (HttpResponse(StatusCodes.Forbidden), StatusCodes.Forbidden),
      (HttpResponse(StatusCodes.Unauthorized), StatusCodes.Unauthorized),
      (HttpResponse(StatusCodes.InternalServerError), StatusCodes.BadGateway),
      (HttpResponse(StatusCodes.BadGateway), StatusCodes.BadGateway),
      (HttpResponse(StatusCodes.GatewayTimeout), StatusCodes.GatewayTimeout)
    )

    "map error responses to correct status codes" in {
      forAll(errorResponses) { (errorResponse, expectedErrorCode) =>
        val request = Get(s"http://example.com/${UUID.randomUUID().toString}")
        when(cl.apply(request))
          .thenReturn(Future.successful(errorResponse))
        client.forward(request).futureValue.status shouldBe expectedErrorCode
      }
    }

  }

}
