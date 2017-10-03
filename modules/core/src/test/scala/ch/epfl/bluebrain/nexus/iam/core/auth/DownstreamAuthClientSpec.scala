package ch.epfl.bluebrain.nexus.iam.core.auth

import java.util.UUID

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.client.RequestBuilding._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import cats.instances.future._
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DownstreamAuthClientSpec extends WordSpecLike
  with Matchers
  with MockitoSugar
  with BeforeAndAfter
  with TableDrivenPropertyChecks
  with ScalaFutures {

  implicit val cl = mock[UntypedHttpClient[Future]]
  val client = DownstreamAuthClient()

  before {
    Mockito.reset(cl)
  }

  "DownstreamAuthClient" should {
    val successResponses = Table(
      "response",
      Future.successful(HttpResponse(StatusCodes.OK, entity ="""{"response": "OK"}""")),
      Future.successful(HttpResponse(StatusCodes.Found).addHeader(Location(Uri("https://example.com/redirect"))))
    )

    "forward requests and return successful responses" in {
      forAll(successResponses) { expectedResp =>
        val request = Get(s"http://example.com/${UUID.randomUUID().toString}")
        when(cl.apply(request))
          .thenReturn(expectedResp)

        client.forward(request) shouldBe expectedResp
      }
    }




    val errorResponses = Table(
      ("downstream response", "expected status code"),
      (Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.BadRequest)))         , StatusCodes.InternalServerError),
      (Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Forbidden)))          , StatusCodes.Forbidden),
      (Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized)))       , StatusCodes.Unauthorized),
      (Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.InternalServerError))), StatusCodes.BadGateway),
      (Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.BadGateway)))         , StatusCodes.BadGateway),
      (Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.GatewayTimeout)))     , StatusCodes.GatewayTimeout)
    )

    "map error responses to correct status codes" in {
      forAll(errorResponses) { (errorResponse, expectedErrorCode) =>
        val request = Get(s"http://example.com/${UUID.randomUUID().toString}")
        when(cl.apply(request))
          .thenReturn(errorResponse)
        client.forward(request).futureValue.status shouldBe expectedErrorCode
      }
    }

  }

}
