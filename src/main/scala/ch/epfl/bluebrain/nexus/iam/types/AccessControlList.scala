package ch.epfl.bluebrain.nexus.iam.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Type definition representing a mapping of identities to permissions for a specific resource.
  *
  * @param value a map of identity and Set of Permission
  */
final case class AccessControlList(value: Map[Identity, Set[Permission]]) {

  /**
    * @return a collapsed Set of [[Permission]] from all the identities
    */
  def permissions: Set[Permission] = value.foldLeft(Set.empty[Permission])(_ ++ _._2)

  /**
    * @return ''true'' if the underlying list is empty or if any pair is found with an empty permissions set
    */
  def hasVoidPermissions: Boolean = value.isEmpty || value.exists { case (_, perms) => perms.isEmpty }

  /**
    * Generates a new [[AccessControlList]] only containing the provided ''identities''.
    *
    * @param identities the identities to be filtered
    */
  def filter(identities: Set[Identity]): AccessControlList =
    AccessControlList(value.filterKeys(identities.contains))
}

object AccessControlList {

  /**
    * An empty [[AccessControlList]].
    */
  val empty: AccessControlList = AccessControlList(Map.empty[Identity, Set[Permission]])

  /**
    * Convenience factory method to build an ACL from var args of ''Identity'' to ''Permissions'' tuples.
    */
  def apply(acl: (Identity, Set[Permission])*): AccessControlList =
    AccessControlList(acl.toMap)
}
