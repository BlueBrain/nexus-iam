package ch.epfl.bluebrain.nexus.iam.service.directives

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, MissingHeaderRejection}
trait CredentialsDirectives {
  def extractBearerToken: Directive1[OAuth2BearerToken] = {
    extractCredentials.flatMap {
      case Some(cred: OAuth2BearerToken) => provide(cred)
      case _                             => reject(MissingHeaderRejection(Authorization.name))
    }
  }
}
object CredentialsDirectives extends CredentialsDirectives
