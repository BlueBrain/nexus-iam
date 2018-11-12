package ch.epfl.bluebrain.nexus.iam.acls

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.types.Permission

/**
  * Type definition representing a mapping of identities to permissions for a specific resource.
  *
  * @param value a map of identity and Set of Permission
  */
final case class AccessControlList(value: Map[Identity, Set[Permission]]) {

  /**
    * Adds the provided ''acl'' to the current ''value'' and returns a new [[AccessControlList]] with the added ACL.
    *
    * @param acl the acl to be added
    */
  def ++(acl: AccessControlList): AccessControlList = {
    val toAddKeys   = acl.value.keySet -- value.keySet
    val toMergeKeys = acl.value.keySet -- toAddKeys
    val added       = value ++ acl.value.filterKeys(toAddKeys.contains)
    val merged = value.filterKeys(toMergeKeys.contains).map {
      case (ident, perms) => ident -> (perms ++ acl.value.getOrElse(ident, Set.empty))
    }
    AccessControlList(added ++ merged)
  }

  /**
    * removes the provided ''acl'' from the current ''value'' and returns a new [[AccessControlList]] with the subtracted ACL.
    *
    * @param acl the acl to be subtracted
    */
  def --(acl: AccessControlList): AccessControlList =
    AccessControlList(acl.value.foldLeft(value) {
      case (acc, (p, aclToSubtract)) =>
        acc.get(p).map(_ -- aclToSubtract) match {
          case Some(remaining) if remaining.isEmpty => acc - p
          case Some(remaining)                      => acc + (p -> remaining)
          case None                                 => acc
        }
    })

  /**
    * @return a collapsed Set of [[Permission]] from all the identities
    */
  def permissions: Set[Permission] = value.foldLeft(Set.empty[Permission]) { case (acc, (_, perms)) => acc ++ perms }

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
