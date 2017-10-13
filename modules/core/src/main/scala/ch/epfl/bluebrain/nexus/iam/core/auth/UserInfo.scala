package ch.epfl.bluebrain.nexus.iam.core.auth

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._

/**
  * Detailed user information.
  *
  * @param sub               the subject (typically corresponds to the user id)
  * @param name              the name of the user
  * @param preferredUsername the preferred user name (used for login purposes)
  * @param givenName         the given name
  * @param familyName        the family name
  * @param email             the email
  * @param groups            the collection of groups that this user belongs to
  */
final case class UserInfo(sub: String,
                          name: String,
                          preferredUsername: String,
                          givenName: String,
                          familyName: String,
                          email: String,
                          groups: Set[String]) {

  /**
    * @param origin the authentication provider realm
    * @return the set of all [[Identity]] references that this user belongs to
    */
  def identities(origin: Uri): Set[Identity] =
    Set(Anonymous, AuthenticatedRef(origin), UserRef(origin, sub)) ++ groups.map(g => GroupRef(origin, g))

  /**
    * Converts this object to a [[User]] instance.
    * @param origin the authentication provider realm
    */
  def toUser(origin: Uri): User = AuthenticatedUser(identities(origin))
}