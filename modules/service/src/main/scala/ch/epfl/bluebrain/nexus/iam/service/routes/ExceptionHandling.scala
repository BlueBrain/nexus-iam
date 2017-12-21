package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.IllegalUriException
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse
import ch.epfl.bluebrain.nexus.commons.service.directives.ErrorDirectives._
import ch.epfl.bluebrain.nexus.commons.service.directives.StatusFrom
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.service.auth.AuthenticationFailure._
import ch.epfl.bluebrain.nexus.iam.service.auth.{AuthenticationFailure, TokenValidationFailure}
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejection._
import io.circe.DecodingFailure
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import journal.Logger

/**
  * Total exception handling logic for the service.
  * It provides an exception handler implementation that ensures
  * all rejections and unexpected failures are gracefully handled
  * and presented to the caller.
  */
object ExceptionHandling {

  private val logger = Logger[this.type]

  /**
    * @return an ExceptionHandler for [[ch.epfl.bluebrain.nexus.commons.types.Rejection]] and
    *         [[ch.epfl.bluebrain.nexus.commons.types.Err]] subtypes that ensures a descriptive
    *         message is returned to the caller
    */
  final def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case f: AuthenticationFailure => complete(f: AuthenticationFailure)
    case r: IllegalUriException =>
      val rejection: CommonRejection = IllegalIdentityFormat(r.getMessage, "origin")
      complete(rejection)
    case r: TokenValidationFailure            => complete(r: TokenValidationFailure)
    case r: IllegalIdentityFormat             => complete(r: CommonRejection)
    case r: IllegalPermissionString           => complete(r: CommonRejection)
    case CommandRejected(r: CommandRejection) => complete(r)
    case UnableToDeserializeEvent             => complete(InternalError("Unable to deserialize event"))
    case UnexpectedState(expected, actual) =>
      complete(InternalError(s"System encountered an unexpected state '$actual', expected: '$expected'"))
    // $COVERAGE-OFF$
    case e: Throwable =>
      logger.warn(s"An unexpected error has happened '${e.getMessage}'")
      complete(InternalError("Something bad happened, can't tell you exactly what; want to try again?"))
    // $COVERAGE-ON$
  }

  /**
    * The discriminator is enough to give us a Json representation (the name of the class)
    */
  private implicit val config: Configuration = Configuration.default.withDiscriminator("code")

  private implicit val authFailureStatusFrom: StatusFrom[AuthenticationFailure] = StatusFrom {
    case UnexpectedAuthenticationFailure(UnexpectedUnsuccessfulHttpResponse(response)) => response.status
    case UnexpectedAuthenticationFailure(_: DecodingFailure)                           => BadGateway
    case UnauthorizedCaller                                                            => Unauthorized
    case _                                                                             => InternalServerError
  }

  private implicit val tokenValidationFailureStatusFrom: StatusFrom[TokenValidationFailure] = StatusFrom(
    _ => Unauthorized)

  private implicit val commandStatusFrom: StatusFrom[CommandRejection] = StatusFrom {
    case CannotAddVoidPermissions                 => BadRequest
    case CannotSubtractVoidPermissions            => BadRequest
    case CannotSubtractFromNonexistentPermissions => NotFound
    case CannotSubtractForNonexistentIdentity     => NotFound
    case CannotRemoveForNonexistentIdentity       => NotFound
    case CannotClearNonexistentPermissions        => NotFound
  }

  private implicit val commonRejectionsStatusFrom: StatusFrom[CommonRejection] = StatusFrom(_ => BadRequest)

  private implicit val internalErrorStatusFrom: StatusFrom[InternalError] = StatusFrom(_ => InternalServerError)

  /**
    * An internal error representation that can safely be returned in its json form to the caller.
    *
    * @param code the code displayed as a response (InternalServerError as default)
    */
  private final case class InternalError(code: String = "InternalServerError")

}
