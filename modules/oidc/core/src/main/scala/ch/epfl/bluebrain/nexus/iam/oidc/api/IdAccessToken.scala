package ch.epfl.bluebrain.nexus.iam.oidc.api

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.Decoder

/**
  * An id and access token along with their metadata.
  *
  * @param accessToken the access token issued by the oidc provider
  * @param idToken     the id token issued by the oidc provider
  * @param tokenType   the type of this token
  * @param expiresIn   the number of seconds until this token expires
  */
final case class IdAccessToken(accessToken: String, idToken: String, tokenType: String, expiresIn: Long)

object IdAccessToken {
  final implicit def idAccessTokenDecoder(implicit config: Configuration): Decoder[IdAccessToken] =
    deriveDecoder[IdAccessToken]
}
