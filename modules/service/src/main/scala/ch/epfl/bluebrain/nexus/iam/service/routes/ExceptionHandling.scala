package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.IllegalUriException
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.service.directives.StatusFrom
import ch.epfl.bluebrain.nexus.iam.service.directives.ErrorDirectives._
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejections.{IllegalIdentityFormat, IllegalPermissionString}
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
    * @return an ExceptionHandler for [[ch.epfl.bluebrain.nexus.iam.core.acls.Rejection]] subtypes that ensures a descriptive
    *         message is returned to the caller
    */
  final def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case r: IllegalUriException =>
      val rejection: CommonRejections = IllegalIdentityFormat(r.getMessage, "origin")
      complete(rejection)
    case r: IllegalIdentityFormat             => complete(r: CommonRejections)
    case r: IllegalPermissionString           => complete(r: CommonRejections)
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

  private implicit val commandStatusFrom: StatusFrom[CommandRejection] = StatusFrom {
    case CannotCreateVoidPermissions              => BadRequest
    case CannotAddVoidPermissions                 => BadRequest
    case CannotSubtractVoidPermissions            => BadRequest
    case CannotSubtractAllPermissions             => BadRequest
    case CannotSubtractFromNonexistentPermissions => NotFound
    case CannotSubtractForNonexistentIdentity     => NotFound
    case CannotRemoveForNonexistentIdentity       => NotFound
    case CannotClearNonexistentPermissions        => NotFound
    case CannotCreateExistingPermissions          => Conflict
  }

  private implicit val commonRejectionsStatusFrom: StatusFrom[CommonRejections] = StatusFrom(_ => BadRequest)

  private implicit val internalErrorStatusFrom: StatusFrom[InternalError] = StatusFrom(_ => InternalServerError)

  /**
    * An internal error representation that can safely be returned in its json form to the caller.
    *
    * @param code the code displayed as a response (InternalServerError as default)
    */
  private final case class InternalError(code: String = "InternalServerError")

}
