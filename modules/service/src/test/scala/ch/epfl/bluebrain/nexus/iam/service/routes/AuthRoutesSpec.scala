package ch.epfl.bluebrain.nexus.iam.service.routes

import java.util.UUID

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.auth.{User, UserInfo}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.{JsonLdSerialization, SimpleIdentitySerialization}
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.bbp.userInfoDecoder
import ch.epfl.bluebrain.nexus.iam.service.auth.{DownstreamAuthClient, TokenId}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.{ExecutionContextExecutor, Future}

class AuthRoutesSpec
    extends WordSpecLike
    with Matchers
    with BeforeAndAfter
    with ScalatestRouteTest
    with MockitoSugar
    with ScalaFutures
    with Fixtures
    with Resources {

  implicit val ec: ExecutionContextExecutor = system.dispatcher
  implicit val ucl                          = mock[UntypedHttpClient[Future]]
  implicit val mt: ActorMaterializer        = ActorMaterializer()
  val uicl                                  = HttpClient.withAkkaUnmarshaller[UserInfo]

  private val provider: AppConfig.OidcProviderConfig = oidc.providers(0)
  val cl1                                            = DownstreamAuthClient(ucl, uicl, provider)
  val cl                                             = List[DownstreamAuthClient[Future]](cl1)
  implicit val claimExtractor                        = claim(cl)
  implicit val apiUri: ApiUri                        = ApiUri("localhost:8080/v0")
  val routes                                         = AuthRoutes(cl).routes

  implicit val enc: Encoder[Identity] =
    JsonLdSerialization.identityEncoder(apiUri.base)
  implicit val dec: Decoder[Identity] = SimpleIdentitySerialization.identityDecoder

  before {
    Mockito.reset(ucl)
  }

  val expectedResponse = HttpResponse(StatusCodes.OK)

  "The IAM service" should {
    "forward GET requests to authorize endpoint and return the downstream response" in {
      when(ucl.apply(Get(provider.authorizeEndpoint)))
        .thenReturn(Future.successful(expectedResponse))
      Get("/oauth2/authorize") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }

    "forward GET requests with redirect to authorize endpoint and return the downstream response" in {
      val callback = "https://redirect.example.com/redirect"
      when(ucl.apply(Get(provider.authorizeEndpoint.withQuery(Query("redirect" -> callback)))))
        .thenReturn(Future.successful(expectedResponse))
      Get(s"/oauth2/authorize?redirect=$callback") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }

    "forward requests to token endpoint and return the downstream response" in {
      val code  = UUID.randomUUID.toString
      val state = UUID.randomUUID.toString
      when(ucl.apply(Get(provider.tokenEndpoint.withQuery(Query("code" -> code, "state" -> state)))))
        .thenReturn(Future.successful(expectedResponse))
      Get(s"/oauth2/token/realm?${Query("code" -> code, "state" -> state).toString}") ~> routes ~> check {
        response shouldBe expectedResponse
      }
    }

    "reject request to token endpoint without code" in {
      Get(s"/oauth2/token/realm?state=foo") ~> routes ~> check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }
    "reject request to token endpoint without state" in {
      Get(s"/oauth2/token/realm?code=bar") ~> routes ~> check {
        response.status shouldBe StatusCodes.BadRequest
      }
    }

    "forward requests to userinfo endpoint and return the downstream response" in {
      val credentials = genCredentailsNoUserInfo(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)
      val userInfo = UserInfo("sub",
                              "name",
                              "preferredUsername",
                              "givenName",
                              "familyName",
                              "email@example.com",
                              Set("group1", "group2"))
      val entity = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, userInfo.asJson.noSpaces))
      when(ucl.apply(Get(provider.userinfoEndpoint).addCredentials(credentials))).thenReturn(Future.successful(entity))

      Get(s"/oauth2/userinfo") ~> addCredentials(credentials) ~> routes ~> check {
        responseAs[UserInfo] shouldEqual userInfo

      }
    }

    "forward requests to userinfo endpoint and return unathorized" in {
      val credentials = genCredentailsNoUserInfo(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)

      when(ucl.apply(Get(provider.userinfoEndpoint).addCredentials(credentials)))
        .thenReturn(Future.failed(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized))))
      Get(s"/oauth2/userinfo") ~> addCredentials(credentials) ~> routes ~> check {
        response.status shouldBe StatusCodes.Unauthorized
      }
    }

    "resolve the userinfo request using the JWT payload" in {
      val credentials = genCredentials(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)

      Get(s"/oauth2/userinfo") ~> addCredentials(credentials) ~> routes ~> check {
        responseAs[UserInfo] shouldEqual userInfo

      }
    }

    "request user endpoint and return a user entity response" in {
      val credentials = genCredentailsNoUserInfo(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)
      val uinfo = UserInfo("sub",
                           "name",
                           "preferredUsername",
                           "givenName",
                           "familyName",
                           "email@example.com",
                           Set("group1", "group2"))
      val user = uinfo.toUser("realm")

      val entity = HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, uinfo.asJson.noSpaces))
      when(ucl.apply(Get(provider.userinfoEndpoint).addCredentials(credentials))).thenReturn(Future.successful(entity))

      Get(s"/oauth2/user") ~> addCredentials(credentials) ~> routes ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[User] shouldEqual user
      }
    }

    "resolve the user request using the JWT payload" in {
      val credentials = genCredentials(TokenId("http://example.com/issuer", "kid"), randomRSAKey.getPrivate)
      val userJson        = jsonContentOf("/auth/user.json")
      Get(s"/oauth2/user") ~> addCredentials(credentials) ~> routes ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[Json] shouldEqual userJson
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
