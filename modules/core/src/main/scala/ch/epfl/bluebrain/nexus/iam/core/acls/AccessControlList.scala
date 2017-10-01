package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.iam.core.identity.Identity

import scala.collection.breakOut

/**
  * Type definition representing a mapping of identities to permissions for a specific resource.
  *
  * @param acl a list of [[AccessControl]] pairs.
  */
final case class AccessControlList(acl: List[AccessControl]) {

  /**
    * @return a ''Map'' projection of the underlying pairs of identities and their permissions
    */
  def toMap: Map[Identity, Permissions] = acl.map { case AccessControl(id, ps) => id -> ps }(breakOut)

  /**
    * @return ''true'' if the underlying list is empty or if any pair is found with an empty permissions set
    */
  def hasVoidPermissions: Boolean = acl.isEmpty || acl.exists(_.permissions.isEmpty)
}

object AccessControlList {

  /**
    * Convenience factory methods to build an ACL from var args of ''Identity'' to ''Permissions'' tuples.
    */
  def apply(acl: (Identity, Permissions)*): AccessControlList =
    new AccessControlList(acl.map { case (id, ps) => AccessControl(id, ps) }.toList)
}
