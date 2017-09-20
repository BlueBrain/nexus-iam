package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.service.types.Error

/**
  * Generic mapper of throwables to proper http responses to be used in computing route completion statements.
  */
object ErrorMapper extends (Throwable => (StatusCode, Error)) {
  override def apply(th: Throwable): (StatusCode, Error) = th match {
    case CommandRejected(rej) =>
      BadRequest -> Error("V101", "Requested command cannot be executed", Some(rej.toString))
    case UnableToDeserializeEvent =>
      BadRequest -> Error("V102", "Unable to deserialize event")
    case UnexpectedState(expected, actual) =>
      BadRequest -> Error("V103", s"System encountered an unexpected state '$actual'", Some(s"Expected: '$expected'"))
    case _ =>
      InternalServerError -> Error("E500", "Something bad happened, can't tell you exactly what; want to try again?")
  }
}
