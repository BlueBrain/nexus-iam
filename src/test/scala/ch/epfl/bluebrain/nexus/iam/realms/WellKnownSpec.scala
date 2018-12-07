package ch.epfl.bluebrain.nexus.iam.realms

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.HttpResponse
import cats.effect.IO
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.test.io.IOEitherValues
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.WellKnownSpec._
import ch.epfl.bluebrain.nexus.iam.types.GrantType._
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import io.circe.Json
import io.circe.parser._
import org.mockito.IdiomaticMockito
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

//noinspection TypeAnnotation
class WellKnownSpec extends WordSpecLike with Matchers with EitherValues with IOEitherValues with IdiomaticMockito {

  private def httpMock(openIdConfig: Json, jwks: Json): HttpClient[IO, Json] = {
    val cl = mock[HttpClient[IO, Json]]
    cl.apply(Get(openIdUrlString)) shouldReturn IO.pure(openIdConfig)
    cl.apply(Get(jwksUrlString)) shouldReturn IO.pure(jwks)
    cl
  }

  "A WellKnow" should {

    "be constructed correctly" in {
      implicit val cl = httpMock(validOpenIdConfig, validJwks)
      val wk          = WellKnown[IO](openIdUrl).accepted
      wk.issuer shouldEqual issuer
      wk.grantTypes shouldEqual Set(AuthorizationCode, Implicit, RefreshToken, Password, ClientCredentials)
      wk.keys shouldEqual Set(validKeyJson)
    }

    "fail to construct" when {
      "the client records a bad response" in {
        implicit val cl = mock[HttpClient[IO, Json]]
        cl.apply(Get(openIdUrlString)) shouldReturn IO.raiseError(UnexpectedUnsuccessfulHttpResponse(HttpResponse()))
        val rej = WellKnown[IO](openIdUrl).rejected[UnsuccessfulOpenIdConfigResponse]
        rej.document shouldEqual openIdUrl
      }
      "the openid contains an invalid issuer" in {
        implicit val cl = httpMock(validOpenIdConfig.deepMerge(Json.obj("issuer" -> Json.fromString(" "))), validJwks)
        val rej         = WellKnown[IO](openIdUrl).rejected[IllegalIssuerFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".issuer"
      }
      "the openid contains a issuer with an invalid type" in {
        implicit val cl = httpMock(validOpenIdConfig.deepMerge(Json.obj("issuer" -> Json.fromInt(3))), validJwks)
        val rej         = WellKnown[IO](openIdUrl).rejected[IllegalIssuerFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".issuer"
      }
      "the openid contains an invalid jwks_uri" in {
        implicit val cl =
          httpMock(validOpenIdConfig.deepMerge(Json.obj("jwks_uri" -> Json.fromString("asd"))), validJwks)
        val rej = WellKnown[IO](openIdUrl).rejected[IllegalJwksUriFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".jwks_uri"
      }
      "the openid contains a jwks_uri with an invalid type" in {
        implicit val cl =
          httpMock(validOpenIdConfig.deepMerge(Json.obj("jwks_uri" -> Json.fromInt(3))), validJwks)
        val rej = WellKnown[IO](openIdUrl).rejected[IllegalJwksUriFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".jwks_uri"
      }
      "the openid contains a invalid grant_types" in {
        implicit val cl =
          httpMock(validOpenIdConfig.deepMerge(Json.obj("grant_types_supported" -> Json.fromString("incorrect"))),
                   validJwks)
        val rej = WellKnown[IO](openIdUrl).rejected[IllegalGrantTypeFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".grant_types_supported"
      }
      "the openid contains no valid grant_types" in {
        implicit val cl = httpMock(
          validOpenIdConfig.deepMerge(Json.obj("grant_types_supported" -> Json.arr(Json.fromString("incorrect")))),
          validJwks)
        val rej = WellKnown[IO](openIdUrl).rejected[IllegalGrantTypeFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".grant_types_supported[0]"
      }
      "the openid contains no grant_types" in {
        implicit val cl =
          httpMock(validOpenIdConfig.deepMerge(Json.obj("grant_types_supported" -> Json.arr())), validJwks)
        val rej = WellKnown[IO](openIdUrl).rejected[IllegalGrantTypeFormat]
        rej.document shouldEqual openIdUrl
        rej.location shouldEqual ".grant_types_supported"
      }
      "the client returns a bad response for the jwks document" in {
        implicit val cl = mock[HttpClient[IO, Json]]
        cl.apply(Get(openIdUrlString)) shouldReturn IO.pure(validOpenIdConfig)
        cl.apply(Get(jwksUrlString)) shouldReturn IO.raiseError(UnexpectedUnsuccessfulHttpResponse(HttpResponse()))
        val rej = WellKnown[IO](openIdUrl).rejected[UnsuccessfulJwksResponse]
        rej.document shouldEqual jwksUrl
      }
      "the jwks document has an incorrect format" in {
        implicit val cl = httpMock(validOpenIdConfig, Json.obj())
        val rej         = WellKnown[IO](openIdUrl).rejected[IllegalJwkFormat]
        rej.document shouldEqual jwksUrl
      }
      "the jwks document has no keys" in {
        implicit val cl = httpMock(validOpenIdConfig, Json.obj("keys" -> Json.arr()))
        val rej         = WellKnown[IO](openIdUrl).rejected[NoValidKeysFound]
        rej.document shouldEqual jwksUrl
      }
      "the jwks document has incorrect keys" in {
        implicit val cl = httpMock(validOpenIdConfig, Json.obj("keys" -> Json.arr(Json.fromString("incorrect"))))
        val rej         = WellKnown[IO](openIdUrl).rejected[NoValidKeysFound]
        rej.document shouldEqual jwksUrl
      }
    }

  }

}

//noinspection TypeAnnotation
object WellKnownSpec {
  import EitherValues._

  val openIdUrlString = "https://localhost/auth/realms/master/.well-known/openid-configuration"
  val openIdUrl       = Url(openIdUrlString).right.value
  val jwksUrlString   = "https://localhost/auth/realms/master/protocol/openid-connect/certs"
  val jwksUrl         = Url(jwksUrlString).right.value
  val issuer          = "https://localhost/auth/realms/master"

  val validOpenIdConfigString =
    s"""
      | {
      |   "issuer": "$issuer",
      |   "jwks_uri": "$jwksUrlString",
      |   "grant_types_supported": [
      |     "authorization_code",
      |     "implicit",
      |     "refresh_token",
      |     "password",
      |     "client_credentials"
      |   ]
      | }
    """.stripMargin
  val validOpenIdConfig = parse(validOpenIdConfigString).right.value

  val validKey =
    """
      | {
      |   "kid": "AlALVuZlTj0NoBS4T1HOolEPeCmH0QnmqtXBtoqIxyc",
      |   "kty": "RSA",
      |   "alg": "RS256",
      |   "use": "sig",
      |   "n": "hqdh70sbz5WcdqJm8RiLGR0rhybItynHbbS9lB7kG1WJohqxnKxBZeH-mGUDCKKNsZYTX7eH3tN5UnwahnYNN1NaMabI2w3x4Sazc7nyYaEWHClvnv5p8SY_esVWXLbcMOrzvEzlTASZxgzrjbmHJDtarZikYNqDdXYk6U_xeZHnTnzOCT3wk4c0RhYrCCEjrXADu5jCZrjxj6nvF3WMLGJGMdifL6IhOxphtXyeG0OxwV4RavHfnknjvl6cgcLm82zwugnjCjD8P_gK7hkpBYk5EIfY4j8T2zjWnnGUQawYHr4L3hOm7o6WfxmhipaVDyOVAohJLZPCDiPP6qHFJw",
      |   "e": "AQAB"
      | }
    """.stripMargin
  val validKeyJson = parse(validKey).right.value

  val validJwksString =
    s"""
    | {
    |   "keys": [
    |     $validKey
    |   ]
    | }
  """.stripMargin

  val validJwks = parse(validJwksString).right.value

}
