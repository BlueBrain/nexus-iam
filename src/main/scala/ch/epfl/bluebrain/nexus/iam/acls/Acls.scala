package ch.epfl.bluebrain.nexus.iam.acls

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.service.http.Path

class Acls[F[_]] {

  /**
    * Creates or appends ''acl'' on a ''path''
    *
    * @param path the path
    * @param acl  the identity to permissions mapping to create
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def add(path: Path, acl: AccessControlList)(implicit ctx: Identity): F[Unit] = ???

  /**
    * Subtracts ''permissions'' on a ''path'' for an ''identity''.
    *
    * @param path        the path
    * @param identity    the target identity
    * @param permissions the permissions to subtract
    * @param ctx         the implicit identity context calling this action
    * @return the resulting permissions in an ''F[_]'' context
    */
  def subtract(path: Path, identity: Identity, permissions: Set[Permission])(
      implicit ctx: Identity): F[Set[Permission]] = ???

  /**
    * Overrides ''acl'' on a ''path''
    *
    * @param path the path
    * @param acl  the identity to permissions mapping to create
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def replace(path: Path, acl: AccessControlList)(implicit ctx: Identity): F[Unit] = ???

  /**
    * Clears all permissions on a ''path''.
    *
    * @param path the path
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def clear(path: Path)(implicit ctx: Identity): F[Unit] = ???

  /**
    * Fetches the entire ACLs for a ''path''.
    *
    * @param path the path for where to fetch the ACls
    */
  def fetch(path: Path): F[AccessControlLists] = ???
}
