package ch.epfl.bluebrain.nexus.iam.oidc.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.iam.oidc.api.Fault
import ch.epfl.bluebrain.nexus.iam.oidc.api.Fault._
import ch.epfl.bluebrain.nexus.iam.oidc.api.Rejection.{AuthorizationAttemptWithInvalidState, IllegalRedirectUri}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

object ExceptionHandling {

  private implicit val config: Configuration = Configuration.default.withDiscriminator("code")

  /**
    * @return an ExceptionHandler for [[Fault]] subtypes that ensures a descriptive message is returned to the caller
    */
  final def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case f: Rejected                   => complete(StatusCodes.BadRequest          -> Error(f))
    case f: TimedOut                   => complete(StatusCodes.GatewayTimeout      -> Error(f))
    case f: Unexpected                 => complete(StatusCodes.InternalServerError -> Error(f))
    case f: UnsuccessfulDownstreamCall => complete(StatusCodes.BadGateway          -> Error(f))
    case f: InternalFault              => complete(StatusCodes.InternalServerError -> Error(f))
    case f: Unauthorized.type          => complete(StatusCodes.Unauthorized        -> Error(f))
  }

  /**
    * Data type strictly used to describe an erroneous response
    * @param code    a machine readable code for the failure
    * @param message a human readable message for the failure
    */
  final case class Error(code: String, message: String)

  object Error {

    /**
      * Constructs an ''Error'' type from a [[Fault]].
      */
    final def apply(f: Fault): Error = f match {
      case _: TimedOut =>
        Error("TimedOut", "A timeout occurred while communicating with a downstream provider")
      case _: Unexpected =>
        Error("InternalError", "An internal server error occurred")
      case _: UnsuccessfulDownstreamCall =>
        Error("UnsuccessfulDownstreamCall", "A call to the downstream provider failed unexpectedly")
      case _: InternalFault =>
        Error("InternalError", "An internal server error occurred")
      case Unauthorized =>
        Error("Unauthorized", "You are not authorized to perform this request")
      case Rejected(IllegalRedirectUri) =>
        Error("IllegalRedirectUri", "The provided redirect uri is invalid")
      case Rejected(AuthorizationAttemptWithInvalidState) =>
        Error("AuthorizationAttemptWithInvalidState", "The provided state is invalid")
    }
  }
}
