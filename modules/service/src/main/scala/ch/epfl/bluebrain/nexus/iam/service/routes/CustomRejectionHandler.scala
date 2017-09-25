package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server._
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSerialization._
import ch.epfl.bluebrain.nexus.iam.service.types.Error
import io.circe.generic.extras.auto._

/**
  * A rejection encapsulates a specific reason why a route was not able to handle a request.
  * Rejections are gathered up over the course of a Route evaluation and finally
  * converted to Error case classes if there was no way for the request to be completed.
  */
object CustomRejectionHandler {
  implicit def instance: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handleAll[MalformedRequestContentRejection] { rejection =>
        val aggregate = rejection.map(_.message).mkString(", ")
        complete(
          BadRequest -> Error(
            "V104",
            "Unable to unmarshall your request. Either wrong JSON format or invalid according to our schema.",
            Some(aggregate)))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(
          MethodNotAllowed -> Error("V105", "Method not supported.", Some(s"Allowed methods: ${names.mkString(", ")}")))
      }
      .handleNotFound {
        complete(
          InternalServerError -> Error("E500",
                                       "Something bad happened, can't tell you exactly what; want to try again?"))
      }
      .result()
}
