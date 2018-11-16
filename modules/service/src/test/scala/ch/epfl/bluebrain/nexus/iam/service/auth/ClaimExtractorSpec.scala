package ch.epfl.bluebrain.nexus.iam.service.auth

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.types.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.AuthenticatedUser
import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.bbp.userInfoDecoder
import ch.epfl.bluebrain.nexus.iam.core.acls.types.UserInfo
import ch.epfl.bluebrain.nexus.iam.service.auth.ClaimExtractor.{JsonSyntax, OAuth2BearerTokenSyntax}
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenValidationFailure._
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.routes.Fixtures
import io.circe.Json
import io.circe.parser._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class ClaimExtractorSpec
    extends TestKit(ActorSystem("ClaimExtractorSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with Fixtures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(5 seconds, 50 milliseconds)

  "A ClaimExtractor" should {

    implicit val ec: ExecutionContext          = system.dispatcher
    implicit val mt: ActorMaterializer         = ActorMaterializer()
    implicit val cl: UntypedHttpClient[Future] = HttpClient.akkaHttpClient
    implicit val downStreamAuthClients =
      oidc.providers.map(DownstreamAuthClient(cl, HttpClient.withAkkaUnmarshaller[UserInfo], _))
    implicit val C = claim(downStreamAuthClients)

    val basicClaim = JwtClaim(
      expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
      issuedAt = Some(Instant.now.getEpochSecond),
      issuer = Some("http://example.com/issuer")
    )

    "fail to extract JWT Claim when the token does not have issuer information" in {
      val cred = OAuth2BearerToken("some")
      ScalaFutures.whenReady(cred.extractClaim.failed) { e =>
        e shouldEqual TokenInvalidOrExpired
      }
    }

    "fail to extract JWT Claim when the token does not contain iss and kid fields" in {
      val token = JwtCirce.encode(basicClaim, randomRSAKey.getPrivate, JwtAlgorithm.RS256)
      val cred  = OAuth2BearerToken(token)

      ScalaFutures.whenReady(cred.extractClaim.failed) { e =>
        e shouldEqual TokenInvalidOrExpired
      }
    }

    "fail to extract JWT Claim when the kid is not found on the provided keys" in {
      val claim = parse(basicClaim.toJson).toOption.get deepMerge Json.obj("kid" -> Json.fromString("otherkid"))
      val token = JwtCirce.encode(claim, randomRSAKey.getPrivate, JwtAlgorithm.RS256)
      val cred  = OAuth2BearerToken(token)

      ScalaFutures.whenReady(cred.extractClaim.failed) { e =>
        e shouldEqual KidOrIssuerNotFound
      }
    }

    "fail to extract JWT Claim when the the signature is not correct" in {
      val claim = parse(basicClaim.toJson).toOption.get deepMerge Json.obj("kid" -> Json.fromString("kid"))
      val token = JwtCirce.encode(claim, randomRSAKey.getPrivate, JwtAlgorithm.RS256)
      val cred  = OAuth2BearerToken(s"$token-Wrong-signature")

      ScalaFutures.whenReady(cred.extractClaim.failed) { e =>
        e shouldEqual TokenInvalidSignature
      }
    }

    "extract JWT successfully" in {
      val claim = parse(basicClaim.toJson).toOption.get deepMerge Json.obj("kid" -> Json.fromString("kid"))
      val token = JwtCirce.encode(claim, randomRSAKey.getPrivate, JwtAlgorithm.RS256)
      val cred  = OAuth2BearerToken(token)
      cred.extractClaim.futureValue._2 shouldEqual
        (claim deepMerge Json.obj("alg" -> Json.fromString("RS256"), "typ" -> Json.fromString("JWT")))
    }

    "attempt to extract user information from JWT" in {
      val claim = parse(basicClaim.toJson).toOption.get deepMerge Json.obj("kid" -> Json.fromString("kid"))
      val token = JwtCirce.encode(claim, randomRSAKey.getPrivate, JwtAlgorithm.RS256)
      val cred  = OAuth2BearerToken(token)

      val f = cred.extractClaim.flatMap { case (_, json) => json.extractUserInfo }

      ScalaFutures.whenReady(f.failed) { e =>
        e shouldEqual TokenUserMetadataNotFound
      }
    }

    "extract user information from JWT successfully" in {
      val claim = parse(basicClaim.toJson).toOption.get
        .deepMerge(parse(
          """{ "kid":"kid", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "name": "Didac Montero Montero Mendez", "groups": ["some", "/other"], "preferred_username": "dmontero", "given_name": "Didac Montero", "family_name": "Montero Mendez", "email": "didac.monteromendez@epfl.ch"}""").toOption.get)
      val token = JwtCirce.encode(claim, randomRSAKey.getPrivate, JwtAlgorithm.RS256)
      val cred  = OAuth2BearerToken(token)

      cred.extractClaim.flatMap { case (client, json) => json.extractUser(client.config) }.futureValue shouldEqual AuthenticatedUser(
        Set(
          GroupRef("realm", "some"),
          UserRef("realm", "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero"),
          GroupRef("realm", "other"),
          AuthenticatedRef(Some("realm")),
          Anonymous()
        ))
    }
  }
}
