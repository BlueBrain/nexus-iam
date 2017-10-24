package ch.epfl.bluebrain.nexus.iam.service.auth

import ch.epfl.bluebrain.nexus.commons.types.Err
import io.circe.{Encoder, Json}

/**
  * Failure type specific to authentication flow, simply wrapping the underlying exception ''cause''.
  */
sealed trait AuthenticationFailure extends Product with Serializable

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object AuthenticationFailure {

  final case object UnauthorizedCaller
      extends Err("The caller is not permitted to perform this request")
      with AuthenticationFailure

  final case class UnexpectedAuthenticationFailure(cause: Throwable)
      extends Err("Error received from downstream authentication provider")
      with AuthenticationFailure

  implicit val authFailureEncoder: Encoder[AuthenticationFailure] = Encoder.encodeJson.contramap {
    case UnauthorizedCaller =>
      Json.obj(
        "code"        -> Json.fromString("UnauthorizedCaller"),
        "description" -> Json.fromString(UnauthorizedCaller.message)
      )
    case f @ UnexpectedAuthenticationFailure(cause) =>
      Json.obj(
        "code"        -> Json.fromString("UnexpectedAuthenticationFailure"),
        "description" -> Json.fromString(f.message),
        "cause"       -> Option(cause.getMessage).fold(Json.Null)(Json.fromString)
      )
  }
}
