package ch.epfl.bluebrain.nexus.iam.core.acls.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.{AuthenticatedUser, User}
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

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
    * @param realm the authentication provider realm
    * @return the set of all [[ch.epfl.bluebrain.nexus.commons.types.identity.Identity]] references that this user belongs to
    */
  def identities(realm: String): Set[Identity] =
    Set(Anonymous(), AuthenticatedRef(Some(realm)), UserRef(realm, sub)) ++ groups.map(g => GroupRef(realm, g))

  /**
    * Converts this object to a [[User]] instance.
    * @param realm the authentication provider realm
    */
  def toUser(realm: String): User = AuthenticatedUser(identities(realm))

}

object UserInfo {
  private implicit val config: Configuration      = Configuration.default.withSnakeCaseMemberNames
  implicit val userInfoEncoder: Encoder[UserInfo] = deriveEncoder[UserInfo]
}
