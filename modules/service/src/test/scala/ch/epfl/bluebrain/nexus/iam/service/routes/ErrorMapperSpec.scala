package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import ch.epfl.bluebrain.nexus.common.types.Err
import ch.epfl.bluebrain.nexus.iam.core.acls.Rejection.CannotAddVoidPermissions
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.service.types.Error
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class ErrorMapperSpec extends WordSpecLike with Matchers with Inspectors {

  private case object UnmatchedError extends Err("Unmatched error")

  private val defaultError = InternalServerError -> Error(
    "E500",
    "Something bad happened, can't tell you exactly what; want to try again?")

  "An ErrorMapper" should {
    "properly map Err to (StatusCode -> Error)" in {
      val mapping = List[(Err, (StatusCode, Error))](
        CommandRejected(CannotAddVoidPermissions) ->
          (BadRequest -> Error("V101",
                               "Requested command cannot be executed",
                               Some(CannotAddVoidPermissions.toString))),
        UnableToDeserializeEvent ->
          (BadRequest            -> Error("V102", "Unable to deserialize event")),
        UnexpectedState[State.Current, State.Initial]() ->
          (BadRequest -> Error("V103",
                               s"System encountered an unexpected state '${State.Initial}'",
                               Some(s"Expected: '${State.Current}'"))),
        UnmatchedError -> defaultError
      )

      forAll(mapping) {
        case (fault, expected) =>
          ErrorMapper(fault) shouldEqual expected
      }
    }
  }
}
