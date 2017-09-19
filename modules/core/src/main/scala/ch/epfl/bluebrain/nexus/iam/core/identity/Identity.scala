package ch.epfl.bluebrain.nexus.iam.core.identity

import akka.http.scaladsl.model.Uri

import cats.Show

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable

/**
  * Represents identities that were authenticated from a third party origin.
  */
trait Authenticated {

  /**
    * @return the third party domain from where the authentication origins
    */
  def origin: Uri
}

object Identity {

  /**
    * The ''user'' identity class.
    *
    * @param origin the authentication's origin
    * @param subject the JWT ''sub'' field
    */
  final case class UserRef(origin: Uri, subject: String) extends Identity with Authenticated

  /**
    * The ''group'' identity class.
    *
    * @param origin the authentication's origin
    * @param group the group name
    */
  final case class GroupRef(origin: Uri, group: String) extends Identity with Authenticated

  /**
    * The ''authenticated'' identity class that represents anyone authenticated from ''origin''.
    *
    * @param origin
    */
  final case class AuthenticatedRef(origin: Uri) extends Identity with Authenticated

  /**
    * The ''anonymous'' identity singleton that covers unknown and unauthenticated users.
    */
  final case object Anonymous extends Identity

  implicit val identityShow: Show[Identity] = Show.fromToString[Identity]

}
