package ch.epfl.bluebrain.nexus.iam.oidc.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{AuthenticationFailedRejection, RejectionHandler}
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.iam.oidc.routes.ExceptionHandling.Error
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

object RejectionHandling {

  /**
    * Defines the custom handling of rejections. When multiple rejections are generated in the routes evaluation
    * process, the priority order to handle them is defined by the order of appearance in this method.
    */
  final def rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case _: AuthenticationFailedRejection =>
          complete(StatusCodes.Unauthorized -> Error("Unauthorized", "You are not authorized to perform this request"))
      }
      .result()
}
