package ch.epfl.bluebrain.nexus.iam.service.groups

import ch.epfl.bluebrain.nexus.iam.types.Identity.GroupRef
import ch.epfl.bluebrain.nexus.iam.core.{AuthenticatedUser, User}

object UserGroupsOps {

  /**
    * Syntax sugar to expose methods on ''User''
    * @param user the user
    */
  implicit class UserGroupsSyntax(user: User) {

    /**
      * Add ''usedGroups'' to the ''user'' and remove the rest
      *
      * @param usedGroups the groups to add to the current user. The rest will be removed
      */
    def filterGroups(usedGroups: Set[GroupRef]): User =
      user match {
        case au @ AuthenticatedUser(identities) =>
          au.copy(identities = identities.filter {
            case group: GroupRef => usedGroups(group)
            case _               => true
          })
        case other => other
      }
  }
}
