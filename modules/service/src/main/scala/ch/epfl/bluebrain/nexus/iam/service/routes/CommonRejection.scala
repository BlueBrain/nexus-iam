package ch.epfl.bluebrain.nexus.iam.service.routes

import ch.epfl.bluebrain.nexus.commons.types.{Err, Rejection}

sealed trait CommonRejection extends Rejection

object CommonRejection {

  /**
    * Signals the inability to parse a json structure into a [[ch.epfl.bluebrain.nexus.commons.iam.identity.Identity]]
    * instance.
    *
    * @param message a human readable description of the cause
    * @param field   the offending field
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalIdentityFormat(override val message: String, field: String)
      extends Err(message)
      with CommonRejection

  /**
    * Signals the inability to convert a permissions string into a [[ch.epfl.bluebrain.nexus.commons.iam.acls.Permission]]
    *
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalPermissionString(override val message: String) extends Err(message) with CommonRejection
}
