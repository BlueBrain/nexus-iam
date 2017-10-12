package ch.epfl.bluebrain.nexus.iam.oidc.config

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import io.circe.{DecodingFailure, ParsingFailure}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

//noinspection TypeAnnotation
class OidcProviderConfigSpec
    extends TestKit(ActorSystem("OidcProviderConfigSpec"))
    with WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfter
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val mt = ActorMaterializer()
  implicit val cl = mock[UntypedHttpClient[Future]]
  val wn =
    s"""
       |{
       |  "authorization_endpoint": "http://localhost/protocol/openid-connect/auth",
       |  "token_endpoint": "http://localhost/protocol/openid-connect/token",
       |  "token_introspection_endpoint": "http://localhost/protocol/openid-connect/token/introspect",
       |  "userinfo_endpoint": "http://localhost/protocol/openid-connect/userinfo",
       |  "jwks_uri": "http://localhost/protocol/openid-connect/certs"
       |}
     """.stripMargin

  before {
    Mockito.reset(cl)
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 50 milliseconds)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "An OidcProviderConfig" should {
    val wnUri = "http://localhost/.well-known/openid-configuration"

    "properly decode a well formed well known document" in {
      when(cl.apply(HttpRequest(uri = wnUri)))
        .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, wn))))

      val expected = OidcProviderConfig(
        authorization = "http://localhost/protocol/openid-connect/auth",
        token = "http://localhost/protocol/openid-connect/token",
        userInfo = "http://localhost/protocol/openid-connect/userinfo",
        jwks = "http://localhost/protocol/openid-connect/certs"
      )
      OidcProviderConfig(wnUri).futureValue shouldEqual expected
    }

    "fail to decode an incorrect well known document" in {
      when(cl.apply(HttpRequest(uri = wnUri)))
        .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, wn + "}"))))
      OidcProviderConfig(wnUri).failed.futureValue shouldBe a[ParsingFailure]
    }

    "fail to decode a well known document with relative uris" in {
      when(cl.apply(HttpRequest(uri = wnUri)))
        .thenReturn(Future.successful(HttpResponse(
          entity = HttpEntity(ContentTypes.`application/json`, wn.replaceAll("http://localhost/", "../")))))
      OidcProviderConfig(wnUri).failed.futureValue shouldBe a[DecodingFailure]
    }
  }

}
