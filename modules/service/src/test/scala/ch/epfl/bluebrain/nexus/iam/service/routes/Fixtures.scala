package ch.epfl.bluebrain.nexus.iam.service.routes

import java.security.{KeyPairGenerator, PrivateKey, PublicKey}
import java.time.Instant

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
import ch.epfl.bluebrain.nexus.iam.service.auth.{ClaimExtractor, DownstreamAuthClient, TokenId}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.{OidcConfig, OidcProviderConfig}
import io.circe.parser.parse
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim, JwtUtils}

import scala.concurrent.Future

trait Fixtures {

  implicit val oidc = OidcConfig(
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

  val generatorRSA = KeyPairGenerator.getInstance(JwtUtils.RSA, JwtUtils.PROVIDER)
  generatorRSA.initialize(1024)
  val randomRSAKey  = generatorRSA.generateKeyPair()
  val randomRSAKey2 = generatorRSA.generateKeyPair()

  implicit val keys: Map[TokenId, PublicKey] = Map(
    TokenId("http://example.com/issuer", "kid")   -> randomRSAKey.getPublic,
    TokenId("http://example.com/issuer2", "kid2") -> randomRSAKey2.getPublic)

  def genCredentials(id: TokenId, privateKey: PrivateKey) = {
    val claim = parse(
      JwtClaim(
        expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
        issuedAt = Some(Instant.now.getEpochSecond),
        issuer = Some(id.iss.toString())
      ).toJson).toOption.get
      .deepMerge(parse(
        s"""{ "kid":"${id.kid}", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "name": "Didac Montero Montero Mendez", "groups": ["some", "/other"], "preferred_username": "dmontero", "given_name": "Didac Montero", "family_name": "Montero Mendez", "email": "didac.monteromendez@epfl.ch"}""").toOption.get)
    val token = JwtCirce.encode(claim, privateKey, JwtAlgorithm.RS256)
    OAuth2BearerToken(token)
  }

  def genCredentailsNoUserInfo(id: TokenId, privateKey: PrivateKey) = {
    val claim = parse(
      JwtClaim(
        expiration = Some(Instant.now.plusSeconds(157784760).getEpochSecond),
        issuedAt = Some(Instant.now.getEpochSecond),
        issuer = Some(id.iss.toString())
      ).toJson).toOption.get
      .deepMerge(parse(
        s"""{ "kid":"${id.kid}", "sub": "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero", "email": "didac.monteromendez@epfl.ch"}""").toOption.get)
    val token = JwtCirce.encode(claim, privateKey, JwtAlgorithm.RS256)
    OAuth2BearerToken(token)
  }
  val userInfo = UserInfo(
    "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero",
    "Didac Montero Montero Mendez",
    "dmontero",
    "Didac Montero",
    "Montero Mendez",
    "didac.monteromendez@epfl.ch",
    Set("some", "other")
  )

  implicit def claim(clients: List[DownstreamAuthClient[Future]]): ClaimExtractor =
    ClaimExtractor.jwtCirceInstance(keys, clients)

}
