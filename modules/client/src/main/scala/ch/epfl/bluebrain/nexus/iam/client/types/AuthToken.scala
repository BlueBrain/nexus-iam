package ch.epfl.bluebrain.nexus.iam.client.types

import akka.http.scaladsl.model.headers.OAuth2BearerToken

/**
  * A data structure which represents a token
  *
  * @param value the token value
  */
final case class AuthToken(value: String)

object AuthToken {
  implicit def toAkka(token: AuthToken): OAuth2BearerToken   = OAuth2BearerToken(token.value)
  implicit def fromAkka(token: OAuth2BearerToken): AuthToken = AuthToken(token.value)
}
