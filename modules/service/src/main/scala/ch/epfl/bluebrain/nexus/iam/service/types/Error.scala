package ch.epfl.bluebrain.nexus.iam.service.types

/**
  * A structured description of an erroneous request outcome.
  *
  * @param code    a machine readable unique code of the outcome
  * @param message a human readable description of the outcome
  * @param details extra hints about the error. Might not always be human readable
  */
final case class Error(code: String, message: String, details: Option[String] = None)
