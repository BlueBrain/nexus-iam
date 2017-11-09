package ch.epfl.bluebrain.nexus.iam.service.auth

import ch.epfl.bluebrain.nexus.commons.types.Err
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder

/**
  * Failure type specific to token validation flow.
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class TokenValidationFailure(message: String) extends Err(message) with Product with Serializable

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object TokenValidationFailure {

  final case object KidOrIssuerNotFound
      extends TokenValidationFailure(
        "The key ID present on the token or the issuer fields do match any key ID present on the JSON Web Key or in the OIDC Provder configuration")

  final case class UnexpectedErrorPublicKeyRetrieval(msg: String)
      extends TokenValidationFailure(
        s"The public key to verify the signature of JWT could not be retrieval because of '$msg'")

  final case object TokenInvalidOrExpired
      extends TokenValidationFailure("The JWK token is invalid format or it expired")

  final case object TokenInvalidSignature extends TokenValidationFailure("The JWK token has invalid signature")

  final case object TokenUserMetadataNotFound
      extends TokenValidationFailure("The JWK token does not contain the user metadata")

  implicit val tokenValidationFailureEncoder: Encoder[TokenValidationFailure] = {
    implicit val config: Configuration = Configuration.default.withDiscriminator("code")
    deriveEncoder[TokenValidationFailure]
  }
}
