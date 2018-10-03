package ch.epfl.bluebrain.nexus.iam.service.auth

import akka.http.scaladsl.model.Uri
import io.circe.Decoder

/**
  * A token unique identifier
  *
  * @param iss the token issuer
  * @param kid the KEY ID of the key
  */
final case class TokenId(iss: Uri, kid: String)

object TokenId {
  final implicit val tokenIdDecoder: Decoder[TokenId] = Decoder.forProduct2[TokenId, String, String]("iss", "kid") {
    case (iss, kid) => TokenId(iss, kid)
  }
}
