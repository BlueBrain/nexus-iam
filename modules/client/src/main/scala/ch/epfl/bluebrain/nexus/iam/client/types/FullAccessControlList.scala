package ch.epfl.bluebrain.nexus.iam.client.types

import scala.collection.immutable.ListMap

/**
  * Type definition representing the ACLs for a list of resources.
  *
  * @param acl a list of [[FullAccessControl]]
  */
final case class FullAccessControlList(acl: List[FullAccessControl]) {

  /**
    * @return a ''Map'' projection of the underlying pairs of paths and their permissions
    */
  def toPathMap: Map[Path, Permissions] =
    acl.foldLeft(ListMap.empty[Path, Permissions]) {
      case (acc, FullAccessControl(_, path, perms)) => acc + (path -> (acc.getOrElse(path, Permissions.empty) ++ perms))
    }

  /**
    * @return a ''Map'' projection of the underlying pairs of identities and their permissions
    *
    */
  def toIdentityMap: Map[Identity, Permissions] =
    acl.foldLeft(ListMap.empty[Identity, Permissions]) {
      case (acc, FullAccessControl(identity, _, perms)) =>
        acc + (identity -> (acc.getOrElse(identity, Permissions.empty) ++ perms))
    }

  /**
    * @return a collapsed [[Permissions]] from all the identities
    */
  def permissions: Permissions = acl.foldLeft(Permissions.empty)(_ ++ _.permissions)

  /**
    * @return ''true'' if the underlying list is empty or if any pair is found with an empty permissions set
    */
  def hasVoidPermissions: Boolean = acl.isEmpty || acl.exists(_.permissions.isEmpty)

  /**
    * Checks whether at least one of the provided ''perms'' is included in this ACLs.
    *
    * @param perms the permissions to check for inclusion in this ACLs
    * @return __true__ if any of the argument permission is included, __false__ otherwise
    */
  def hasAnyPermission(perms: Permissions): Boolean = acl.exists {
    case FullAccessControl(_, _, p) => p.containsAny(perms)
  }

  /**
    * Checks whether every every permission of the provided ''perms'' is included in this ACLs.
    *
    * @param perms the permissions to check for inclusion in this ACLs
    * @return __true__ if any of the argument permission is included, __false__ otherwise
    */
  def hasEveryPermission(perms: Permissions): Boolean = permissions.containsAll(perms)
}

object FullAccessControlList {
  // $COVERAGE-OFF$

  /**
    * Convenience factory method to build an ACL from var args of ''Identity'' to ''Permissions'' tuples.
    */
  def apply(acl: (Identity, Path, Permissions)*): FullAccessControlList =
    new FullAccessControlList(acl.map { case (id, path, ps) => FullAccessControl(id, path, ps) }.toList)

  // $COVERAGE-ON$
}
