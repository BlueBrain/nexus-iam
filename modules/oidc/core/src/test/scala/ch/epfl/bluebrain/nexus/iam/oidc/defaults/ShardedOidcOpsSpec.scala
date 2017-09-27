package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import java.util.UUID

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.HttpMessage.DiscardedEntity
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.oidc.api.Fault.{Rejected, UnsuccessfulDownstreamCall}
import ch.epfl.bluebrain.nexus.iam.oidc.api.{Fault, IdAccessToken, UserInfo}
import ch.epfl.bluebrain.nexus.iam.oidc.api.Rejection.AuthorizationAttemptWithInvalidState
import ch.epfl.bluebrain.nexus.iam.oidc.config.AppConfig.OidcConfig
import ch.epfl.bluebrain.nexus.iam.oidc.config.OidcProviderConfig
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.StateActor.Protocol.{
  AuthState,
  GenState,
  InvalidStateReference,
  ValidateState
}
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.UserInfoActor.Protocol.{GetInfo, Info, SetInfo}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

//noinspection TypeAnnotation
class ShardedOidcOpsSpec
    extends TestKit(ActorSystem("ShardedOidcOpsSpec"))
    with WordSpecLike
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfter
    with BeforeAndAfterAll {

  implicit val mt = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val cl = mock[UntypedHttpClient[Future]]
  implicit val tm = Timeout(3 seconds)

  before {
    Mockito.reset(cl)
    when(cl.discardBytes(any(classOf[HttpEntity])))
      .thenReturn(Future.successful(new DiscardedEntity(Future.successful(Done))))
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 50 milliseconds)

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  class FixedStateShardedOidcOps(stateId: String,
                                 stateRef: ActorRef,
                                 userInfoRef: ActorRef,
                                 cfg: OidcConfig,
                                 pcfg: OidcProviderConfig)
      extends ShardedOidcOps(stateRef, userInfoRef, cfg, pcfg) {
    override protected def generateStateId() = stateId
  }

  "A ShardedOidcOps" should {

    val cfg = OidcConfig(
      discoveryUri = "http://localhost/.well-known/openid-configuration",
      clientId = "clientid",
      clientSecret = "clientsecret",
      scopes = List("openid"),
      tokenUri = "http://localhost/token"
    )

    val pcfg = OidcProviderConfig(
      authorization = "http://localhost/protocol/openid-connect/auth",
      token = "http://localhost/protocol/openid-connect/token",
      userInfo = "http://localhost/protocol/openid-connect/userinfo",
      jwks = "http://localhost/protocol/openid-connect/certs"
    )
    val accessToken = "access_token"

    "build a correct redirect url" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      val ops           = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)
      val q = Query(
        "response_type" -> "code",
        "client_id"     -> cfg.clientId,
        "redirect_uri"  -> cfg.tokenUri.toString(),
        "scope"         -> cfg.scopes.mkString(" "),
        "state"         -> stateId
      )
      val eventualUri = ops.buildRedirectUri(None)
      stateProbe.expectMsg(GenState(stateId, None))
      stateProbe.reply(AuthState(stateId, None))
      eventualUri.futureValue shouldEqual pcfg.authorization.withQuery(q)
    }

    "exchange the code with an id access token" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      val idAccessTokenString =
        s"""
           |{
           |  "id_token":"id_token",
           |  "$accessToken":"$accessToken",
           |  "token_type":"token_type",
           |  "expires_in":1
           |}
         """.stripMargin
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(
          Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, idAccessTokenString))))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val resp = ops.exchangeCode("code", stateId)
      stateProbe.expectMsg(ValidateState(stateId))
      stateProbe.reply(AuthState(stateId, None))
      val (iat, uriOpt) = resp.futureValue
      iat shouldEqual IdAccessToken(accessToken, "id_token", "token_type", 1L)
      uriOpt shouldEqual None
    }

    "fail to exchange code with an invalid state ref" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val resp = ops.exchangeCode("code", stateId)
      stateProbe.expectMsg(ValidateState(stateId))
      stateProbe.reply(InvalidStateReference(stateId))
      resp.failed.futureValue shouldEqual Rejected(AuthorizationAttemptWithInvalidState)
    }

    "fail to exchange code when unable to decode idAccessToken" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()

      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, "{}"))))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val resp = ops.exchangeCode("code", stateId)
      stateProbe.expectMsg(ValidateState(stateId))
      stateProbe.reply(AuthState(stateId, None))

      resp.failed.futureValue shouldBe a[UnsuccessfulDownstreamCall]
    }

    "fail to exchange code when downstream provider responds with a server error" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()

      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(HttpResponse(StatusCodes.InternalServerError)))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val resp = ops.exchangeCode("code", stateId)
      stateProbe.expectMsg(ValidateState(stateId))
      stateProbe.reply(AuthState(stateId, None))

      resp.failed.futureValue shouldBe a[UnsuccessfulDownstreamCall]
    }

    "fail to exchange code when the http client returns an error" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()

      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.failed(new IllegalArgumentException("illegal")))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val resp = ops.exchangeCode("code", stateId)
      stateProbe.expectMsg(ValidateState(stateId))
      stateProbe.reply(AuthState(stateId, None))

      resp.failed.futureValue shouldBe a[UnsuccessfulDownstreamCall]
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
         | "groups": []
         |}
       """.stripMargin
    val userInfo =
      UserInfo("sub", "name", "preferredUsername", "givenName", "familyName", "email@example.com", Set.empty)

    "return a cached user info" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val eventualInfo = ops.getUserInfo(accessToken)
      userInfoProbe.expectMsg(GetInfo(accessToken))
      userInfoProbe.reply(Info(accessToken, Some(userInfo)))
      eventualInfo.futureValue shouldEqual userInfo
    }

    "lookup the user info from the downstream provider" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(
          Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, userInfoString))))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val eventualInfo = ops.getUserInfo(accessToken)
      userInfoProbe.expectMsg(GetInfo(accessToken))
      userInfoProbe.reply(Info(accessToken, None))
      userInfoProbe.expectMsg(SetInfo(accessToken, userInfo))
      eventualInfo.futureValue shouldEqual userInfo
    }

    "fail to lookup user info when unable to decode response" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, "{}"))))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val eventualInfo = ops.getUserInfo(accessToken)
      userInfoProbe.expectMsg(GetInfo(accessToken))
      userInfoProbe.reply(Info(accessToken, None))
      eventualInfo.failed.futureValue shouldBe a[UnsuccessfulDownstreamCall]
    }

    "failed to lookup user info when unauthorized" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(HttpResponse(StatusCodes.Unauthorized)))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val eventualInfo = ops.getUserInfo(accessToken)
      userInfoProbe.expectMsg(GetInfo(accessToken))
      userInfoProbe.reply(Info(accessToken, None))
      eventualInfo.failed.futureValue shouldEqual Fault.Unauthorized
    }

    "failed to lookup user info when provided with an unsuccessful response" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(HttpResponse(StatusCodes.InternalServerError)))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val eventualInfo = ops.getUserInfo(accessToken)
      userInfoProbe.expectMsg(GetInfo(accessToken))
      userInfoProbe.reply(Info(accessToken, None))
      eventualInfo.failed.futureValue shouldBe a[UnsuccessfulDownstreamCall]
    }

    "failed to lookup user info when the http client fails unexpectedly" in {
      val stateId       = UUID.randomUUID().toString
      val stateProbe    = TestProbe()
      val userInfoProbe = TestProbe()
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.failed(new IllegalArgumentException("illegal")))

      val ops = new FixedStateShardedOidcOps(stateId, stateProbe.ref, userInfoProbe.ref, cfg, pcfg)

      val eventualInfo = ops.getUserInfo(accessToken)
      userInfoProbe.expectMsg(GetInfo(accessToken))
      userInfoProbe.reply(Info(accessToken, None))
      eventualInfo.failed.futureValue shouldBe a[UnsuccessfulDownstreamCall]
    }

  }

}
