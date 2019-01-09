package ch.epfl.bluebrain.nexus.iam.routes

sealed abstract class ResourceRejection(val msg: String) extends Product with Serializable

object ResourceRejection {

  /**
    * Signals the inability to convert the requested query parameter.
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalParameter(reason: String) extends ResourceRejection(s"Illegal parameter error '$reason'")

  /**
    * Signals the inability to convert the requested query parameter.
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class Unexpected(reason: String) extends ResourceRejection(s"Unexpected error '$reason'")

}
