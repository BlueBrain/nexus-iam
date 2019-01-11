package ch.epfl.bluebrain.nexus.iam.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.ExceptionHandler
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.routes.ResourceRejection.Unexpected
import ch.epfl.bluebrain.nexus.iam.types.IamError
import ch.epfl.bluebrain.nexus.iam.types.IamError.InvalidAccessToken
import ch.epfl.bluebrain.nexus.service.http.directives.StatusFrom
import journal.Logger

/**
  * It provides an exception handler implementation that ensures all unexpected failures are gracefully handled and presented to the caller.
  */
object ExceptionHandling {

  private val logger = Logger[this.type]

  /**
    * @return an ExceptionHandler that ensures a descriptive message is returned to the caller
    */
  final def apply(): ExceptionHandler =
    ExceptionHandler {
      case err: InvalidAccessToken =>
        // suppress errors for invalid tokens
        complete(iamErrorStatusFrom(err) -> (err: IamError))
      case err: IamError =>
        logger.error("Exception caught during routes processing ", err)
        complete(iamErrorStatusFrom(err) -> err)
      case err =>
        logger.error("Exception caught during routes processing ", err)
        complete(Unexpected("The system experienced an unexpected error, please try again later."): ResourceRejection)
    }

  private def iamErrorStatusFrom: StatusFrom[IamError] = StatusFrom {
    case _: IamError.AccessDenied                => StatusCodes.Forbidden
    case _: IamError.UnexpectedInitialState      => StatusCodes.InternalServerError
    case _: IamError.ReadWriteConsistencyTimeout => StatusCodes.InternalServerError
    case _: IamError.DistributedDataError        => StatusCodes.InternalServerError
    case _: IamError.InternalError               => StatusCodes.InternalServerError
    case _: IamError.InvalidAccessToken          => StatusCodes.Unauthorized
    case IamError.NotFound                       => StatusCodes.NotFound
  }
}
