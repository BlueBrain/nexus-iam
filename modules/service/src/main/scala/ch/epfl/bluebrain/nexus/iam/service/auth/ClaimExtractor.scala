package ch.epfl.bluebrain.nexus.iam.service.auth

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import ch.epfl.bluebrain.nexus.commons.iam.auth.{User, UserInfo}
import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.bbp._
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenId._
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenValidationFailure._
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcProviderConfig
import ch.epfl.bluebrain.nexus.iam.service.routes._
import io.circe._
import journal.Logger
import pdi.jwt.{JwtCirce, JwtOptions}

import scala.concurrent.{ExecutionContext, Future}

abstract class ClaimExtractor extends (OAuth2BearerToken => Future[(DownstreamAuthClient[Future], Json)])

object ClaimExtractor {
  private val log = Logger[this.type]

  /**
    * Constructs a [[ClaimExtractor]] using JWT Circe and the implicitly available values
    *
    * @param store   the credentials store of [[java.security.PublicKey]]s to verify JWT signatures
    * @param clients the list of [DownstreamAuthClient[Future]
    */
  final def apply(store: CredentialsStore, clients: List[DownstreamAuthClient[Future]])(implicit ec: ExecutionContext) =
    new ClaimExtractor {

      private def credentialsToEither(cred: OAuth2BearerToken) =
        JwtCirce
          .decodeJsonAll(cred.token, JwtOptions(signature = false))
          .fold(
            fa => {
              log.warn(s"Error while decoding JWT token '${cred.token}' with message '${fa.getMessage}'")
              Left(TokenInvalidOrExpired)
            }, { case (header, payload, _) => Right(payload deepMerge header) }
          )

      /**
        * Attempts to extract the JWT Claim from the ''cred''
        *
        * @param cred the ''Authorization'' HTTP header credentials.
        */
      override def apply(cred: OAuth2BearerToken): Future[(DownstreamAuthClient[Future], Json)] =
        (for {
          json    <- credentialsToEither(cred)
          tokenId <- json.as[TokenId].fold(_ => Left(TokenInvalidOrExpired), s => Right(s))
          client  <- clients.findByIssuer(tokenId.iss).toRight(KidOrIssuerNotFound)
        } yield (json, tokenId, client)) match {
          case Left(fa) =>
            Future.failed(fa)
          case Right((json, tokenId, client)) =>
            store
              .fetchKey(tokenId)
              .flatMap { key =>
                if (JwtCirce.isValid(cred.token, key)) Future.successful((client, json))
                else Future.failed(TokenInvalidSignature)
              }
        }
    }

  /**
    * Syntax sugar to expose methods on ''OAuth2BearerToken''
    *
    * @param cred the ''Authorization'' HTTP header credentials.
    */
  implicit class OAuth2BearerTokenSyntax(cred: OAuth2BearerToken)(implicit E: ClaimExtractor) {

    /**
      * Exposes a method to extract the JWT Claim encoded in the ''cred''
      *
      * @return a tuple with the ''token'' unique Id and the decoded Claim
      */
    def extractClaim: Future[(DownstreamAuthClient[Future], Json)] = E(cred)
  }

  /**
    * Syntax sugar to expose methods on ''Json''
    *
    * @param claim the JWT Claim
    */
  implicit class JsonSyntax(claim: Json) {

    /**
      * Exposes a method to exctract realm name from ''json'' JWT Claim
      * @param providers list of the OIDC provider config
      * @return the realm name if found
      */
    def extractRealm(providers: List[AppConfig.OidcProviderConfig]): Option[String] = {
      claim.as[TokenId] match {
        case Right(TokenId(iss, _)) => providers.find(_.issuer equals iss).map(_.realm)
        case Left(_)                => None
      }
    }

    /**
      * Exposes a method to extract the user from the ''json'' JWT Claim.
      *
      * @param config the oidc provider configuration
      */
    def extractUser(config: OidcProviderConfig)(implicit ec: ExecutionContext): Future[User] =
      extractUserInfo.map(_.toUser(config.realm))

    /**
      * Exposes a method to extract the userinfo from the ''json'' JWT Claim.
      *
      */
    def extractUserInfo: Future[UserInfo] =
      claim
        .as[UserInfo]
        .fold(_ => Future.failed(TokenUserMetadataNotFound), u => Future.successful(u))
  }

}
