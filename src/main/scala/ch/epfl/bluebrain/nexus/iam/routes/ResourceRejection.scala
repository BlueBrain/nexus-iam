package ch.epfl.bluebrain.nexus.iam.routes

sealed abstract class ResourceRejection(val msg: String) extends Product with Serializable

object ResourceRejection {

  /**
    * Signals the inability to convert the requested query parameter.
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class IllegalParameter(message: String) extends ResourceRejection(s"Illegal parameter error '$message'")

  /**
    * Signals the inability to convert the requested query parameter.
    */
  @SuppressWarnings(Array("IncorrectlyNamedExceptions"))
  final case class Unexpected(message: String) extends ResourceRejection(s"Unexpected error '$message'")

}
