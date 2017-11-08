package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.PublicKey

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.stream.Materializer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.types.Err
import ch.epfl.bluebrain.nexus.iam.service.auth.Jwk.Jwks
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcProviderConfig
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContext, Future}

object JwkClient {

  /**
    * Retrieves the Web Token Key(s) from the provided endpoint in the config
    * and converts them to [[PublicKey]] on a ''Map''.
    *
    * @param config the oidc real config which contains the contains the Web Token Key(s) endpoint
    */
  final def apply(config: OidcProviderConfig)(implicit ucl: UntypedHttpClient[Future],
                                              ec: ExecutionContext,
                                              mt: Materializer): Future[Map[TokenId, PublicKey]] = {

    val cl = HttpClient.withAkkaUnmarshaller[Jwks]

    cl(Get(config.jwkCert))
      .map(_.keys.foldLeft[Either[TokenToPublicKeyError, Map[TokenId, PublicKey]]](Right(Map())) {
        case (err @ Left(_), _) => err
        case (Right(map), jwKey) =>
          jwKey.key.fold(fa => Left(TokenToPublicKeyError(fa)),
                         key => Right(map + (TokenId(config.issuer, jwKey.kid) -> key)))
      })
      .flatMap {
        case Right(map) => Future.successful(map)
        case Left(err)  => Future.failed(err)
      }
  }

  /**
    * Signals the incapability of converting the JWT token into a [[PublicKey]]
    *
    * @param cause extra information about the error
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class TokenToPublicKeyError(cause: Throwable)
      extends Err("Error attempting to convert a token to a public key")

}
