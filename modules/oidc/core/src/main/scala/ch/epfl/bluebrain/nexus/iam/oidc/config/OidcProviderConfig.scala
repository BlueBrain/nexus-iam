package ch.epfl.bluebrain.nexus.iam.oidc.config

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.model.Uri.ParsingMode
import akka.stream.Materializer
import cats.syntax.either._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import io.circe.{Decoder, DecodingFailure, HCursor}

import scala.concurrent.Future
import scala.util.Try

/**
  * Configuration of an OIDC provider derived from the discovery uri.
  *
  * @param authorization the authorization uri
  * @param token the token uri
  * @param userInfo the user info uri
  * @param jwks the jwks uri
  */
final case class OidcProviderConfig(authorization: Uri, token: Uri, userInfo: Uri, jwks: Uri)

object OidcProviderConfig {

  /**
    * Looks up an ''OidcProviderConfig'' at the provided discovery uri.
    *
    * @param discoveryUri an OIDC provider discovery uri, typically ending in ''.../.well-known/openid-configuration''
    */
  final def apply(discoveryUri: Uri)(implicit
                                     as: ActorSystem,
                                     mt: Materializer,
                                     cl: UntypedHttpClient[Future]): Future[OidcProviderConfig] = {
    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
    import as.dispatcher
    val providerConfigClient = implicitly[HttpClient[Future, OidcProviderConfig]]
    providerConfigClient(HttpRequest(uri = discoveryUri))
  }

  final implicit val oidcProviderConfigDecoder: Decoder[OidcProviderConfig] = {
    def decodeUri(cursor: HCursor, name: String): Decoder.Result[Uri] =
      cursor.get[String](name).flatMap { uriString =>
        Try(Uri(uriString, StandardCharsets.UTF_8, ParsingMode.Strict))
          .filter(uri => uri.isAbsolute && uri.scheme.matches("http(?:s)?"))
          .toEither
          .leftMap { _ =>
            DecodingFailure(s"Illegal uri value '$uriString'", cursor.downField(name).history)
          }
      }

    Decoder.instance { cursor =>
      for {
        authorization <- decodeUri(cursor, "authorization_endpoint")
        token         <- decodeUri(cursor, "token_endpoint")
        userInfo      <- decodeUri(cursor, "userinfo_endpoint")
        jwks          <- decodeUri(cursor, "jwks_uri")
      } yield OidcProviderConfig(authorization, token, userInfo, jwks)
    }
  }

}
