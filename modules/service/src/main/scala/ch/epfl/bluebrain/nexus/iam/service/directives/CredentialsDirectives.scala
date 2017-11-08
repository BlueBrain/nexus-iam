package ch.epfl.bluebrain.nexus.iam.service.directives

import akka.http.scaladsl.model.headers.HttpChallenges._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directive1, MissingHeaderRejection}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig

trait CredentialsDirectives {

  /**
    * Directive that provides the [[OAuth2BearerToken]] if present or rejects otherwise
    *
    * @param oidc the implicitly available [[OidcConfig]]
    */
  def extractBearerToken(implicit oidc: OidcConfig): Directive1[OAuth2BearerToken] = {
    extractCredentials.flatMap {
      case Some(cred: OAuth2BearerToken) => provide(cred)
      case Some(_)                       => reject(AuthenticationFailedRejection(CredentialsRejected, oAuth2(oidc.defaultRealm)))
      case _                             => reject(MissingHeaderRejection(Authorization.name))
    }
  }
}
object CredentialsDirectives extends CredentialsDirectives
