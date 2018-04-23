package ch.epfl.bluebrain.nexus.iam.core.acls.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Type definition representing a mapping of identities to permissions for a specific resource.
  *
  * @param acl a set of [[AccessControl]] pairs.
  */
final case class AccessControlList(acl: Set[AccessControl]) {

  /**
    * @return a ''Map'' projection of the underlying pairs of identities and their permissions
    */
  def toMap: Map[Identity, Permissions] = acl.map { case AccessControl(id, ps) => id -> ps }.toMap

  /**
    * @return a collapsed [[Permissions]] from all the identities
    */
  def permissions: Permissions = acl.foldLeft(Permissions.empty)(_ ++ _.permissions)

  /**
    * @return ''true'' if the underlying list is empty or if any pair is found with an empty permissions set
    */
  def hasVoidPermissions: Boolean = acl.isEmpty || acl.exists(_.permissions.isEmpty)
}

object AccessControlList {
  // $COVERAGE-OFF$

  /**
    * Convenience factory method to build an ACL from var args of ''Identity'' to ''Permissions'' tuples.
    */
  def apply(acl: (Identity, Permissions)*): AccessControlList =
    new AccessControlList(acl.map { case (id, ps) => AccessControl(id, ps) }.toSet)

  /**
    * Convenience factory method to build an ACL from a ''mapping'' instance of Map[Identity, Permissions]
    */
  def fromMap(mapping: Map[Identity, Permissions]): AccessControlList = apply(mapping.toList: _*)

  // $COVERAGE-ON$
}
