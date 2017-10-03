package ch.epfl.bluebrain.nexus.iam.service.routes

import ch.epfl.bluebrain.nexus.common.types.Err
import ch.epfl.bluebrain.nexus.iam.core.acls.Rejection

sealed trait CommonRejections extends Rejection

object CommonRejections {

  /**
    * Signals the inability to find a resource associated to a particular HTTP verb
    *
    * @param supported the collections of supported HTTP verbs for a particular resource
    */
  final case class MethodNotSupported(supported: Seq[String]) extends CommonRejections

  /**
    * Signals the inability to convert the Payload into JSON. It can be due to invalid JSON
    * syntax or due to constraints in the implemented JSON Decoder
    *
    * @param details optional explanation about what went wrong while parsing the Json payload
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class WrongOrInvalidJson(details: Option[String]) extends Err("Invalid json") with CommonRejections

  /**
    * Signals the inability to parse a json structure into a [[ch.epfl.bluebrain.nexus.iam.core.identity.Identity]]
    * instance.
    *
    * @param message a human readable description of the cause
    * @param field   the offending field
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalIdentityFormat(override val message: String, field: String)
      extends Err(message)
      with CommonRejections

  /**
    * Signals the inability to convert a permissions string into a [[ch.epfl.bluebrain.nexus.iam.core.acls.Permission]]
    *
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalPermissionString(override val message: String) extends Err(message) with CommonRejections

  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class MissingParameters(val missingParams: Seq[String])
      extends Err(s"""Missing query parameters: ${missingParams.mkString(", ")}""")
      with CommonRejections
}
