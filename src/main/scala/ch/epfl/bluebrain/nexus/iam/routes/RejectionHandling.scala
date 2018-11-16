package ch.epfl.bluebrain.nexus.iam.routes

import akka.http.javadsl.server.AuthorizationFailedRejection
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server._
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection._
import ch.epfl.bluebrain.nexus.iam.routes.ResourceRejection.IllegalParameter
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._

/**
  * A rejection encapsulates a specific reason why a route was not able to handle a request.
  * Rejections are gathered up over the course of a Route evaluation and finally
  * converted to CommonRejections case classes if there was no way for the request to be completed.
  */
object RejectionHandling {

  /**
    * Defines the custom handling of rejections. When multiple rejections are generated
    * in the routes evaluation process, the priority order to handle them is defined
    * by the order of appearance in this method.
    */
  final def apply(): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedQueryParamRejection(_, _, Some(e: HttpRejection)) =>
          complete(BadRequest -> e)
        case MalformedQueryParamRejection(_, _, Some(err)) =>
          complete(IllegalParameter(err.getMessage): ResourceRejection)
        case ValidationRejection(err, _) =>
          complete(IllegalParameter(err): ResourceRejection)
        case MissingQueryParamRejection(param) =>
          complete(MissingParameters(Seq(param)): HttpRejection)
        case _: AuthorizationFailedRejection =>
          complete(Unauthorized -> (UnauthorizedAccess: HttpRejection))
      }
      .handleAll[MalformedRequestContentRejection] { rejection =>
        val aggregate = rejection.map(_.message).mkString(", ")
        complete(BadRequest -> (WrongOrInvalidJson(Some(aggregate)): HttpRejection))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(MethodNotAllowed -> (MethodNotSupported(names): HttpRejection))
      }
      .result()

}
