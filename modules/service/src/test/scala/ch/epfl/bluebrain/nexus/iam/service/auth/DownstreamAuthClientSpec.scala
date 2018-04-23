package ch.epfl.bluebrain.nexus.iam.service.auth

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Location, OAuth2BearerToken}
import akka.stream.ActorMaterializer
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.bbp._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.UserInfo
import ch.epfl.bluebrain.nexus.iam.service.auth.AuthenticationFailure.{
  UnauthorizedCaller,
  UnexpectedAuthenticationFailure
}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.{OidcConfig, OidcProviderConfig}
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import io.circe.DecodingFailure
import io.circe.syntax._
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class DownstreamAuthClientSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfter
    with TableDrivenPropertyChecks
    with ScalaFutures {

  private implicit val oidc = OidcConfig(
    List(
      OidcProviderConfig("realm",
                         "http://example.com/issuer",
                         "http://example.com/cert",
                         "http://example.com/authorize",
                         "http://example.com/token",
                         "http://example.com/userinfo"),
      OidcProviderConfig(
        "realm2",
        "http://example.com/issuer2",
        "http://example.com/cert2",
        "http://example.com/authorize2",
        "http://example.com/token2",
        "http://example.com/userinfo2"
      )
    ),
    "realm"
  )
  implicit val as = ActorSystem("as")
  implicit val mt = ActorMaterializer()
  implicit val cl = mock[UntypedHttpClient[Future]]

  private val uicl   = HttpClient.withAkkaUnmarshaller[UserInfo]
  private val client = DownstreamAuthClient(cl, uicl, oidc.providers(0))

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 100 millis)

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
        when(cl.apply(request)).thenReturn(Future.successful(expectedResp))

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
        when(cl.apply(request)).thenReturn(Future.successful(errorResponse))
        client.forward(request).futureValue.status shouldBe expectedErrorCode
      }
    }

    val userInfoString =
      s"""
         |{
         | "sub": "sub",
         | "name": "name",
         | "preferred_username": "preferredUsername",
         | "given_name": "givenName",
         | "family_name": "familyName",
         | "email": "email@example.com",
         | "groups": ["group"]
         |}
       """.stripMargin
    val user =
      UserInfo("sub", "name", "preferredUsername", "givenName", "familyName", "email@example.com", Set("group"))
        .toUser(oidc.defaultRealm)

    "transform userinfo requests properly" when {
      "authentication is successful" in {
        when(cl.apply(isA(classOf[HttpRequest])))
          .thenReturn(
            Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, userInfoString))))

        client.getUser(OAuth2BearerToken("valid_token")).futureValue shouldEqual user
      }

      "authentication is rejected or an error is received" in {
        forAll(errorResponses) { (errorResponse, expectedErrorCode) =>
          when(cl.apply(isA(classOf[HttpRequest])))
            .thenReturn(Future.failed(UnexpectedUnsuccessfulHttpResponse(errorResponse)))

          val expected =
            if (errorResponse.status == StatusCodes.Unauthorized) UnauthorizedCaller
            else UnexpectedAuthenticationFailure(UnexpectedUnsuccessfulHttpResponse(HttpResponse(expectedErrorCode)))

          client
            .getUser(OAuth2BearerToken("bad_token"))
            .failed
            .futureValue shouldEqual expected
        }
      }

      "decoding the user info object fails" in {
        val df = DecodingFailure("Failed to decode userinfo", List.empty)
        when(cl.apply(isA(classOf[HttpRequest]))).thenReturn(Future.failed(df))

        client.getUser(OAuth2BearerToken("valid_token")).failed.futureValue shouldBe UnexpectedAuthenticationFailure(df)
      }
    }

    val authFailures = Table(
      ("failure", "expected JSON encoding"),
      (UnauthorizedCaller,
       """{"code":"UnauthorizedCaller","description":"The caller is not permitted to perform this request"}"""),
      (UnexpectedAuthenticationFailure(new Exception),
       """{"code":"UnexpectedAuthenticationFailure","description":"Error received from downstream authentication provider","cause":null}"""),
      (UnexpectedAuthenticationFailure(DecodingFailure("Unable to decode userinfo", List.empty)),
       """{"code":"UnexpectedAuthenticationFailure","description":"Error received from downstream authentication provider","cause":"Unable to decode userinfo"}""")
    )

    "encode authentication failure errors to JSON" in {
      forAll(authFailures) { (f: AuthenticationFailure, encoding) =>
        f.asJson.noSpaces shouldEqual encoding
      }
    }
  }
}
