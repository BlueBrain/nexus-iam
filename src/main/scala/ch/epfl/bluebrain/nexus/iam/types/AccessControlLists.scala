package ch.epfl.bluebrain.nexus.iam.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.service.http.Path

import scala.collection.immutable.ListMap

/**
  * Type definition representing a mapping of Paths to AccessControlList for a specific resource.
  *
  * @param value a map of path and AccessControlList
  */
final case class AccessControlLists(value: Map[Path, AccessControlList]) {

  /**
    * Adds the provided ''acls'' to the current ''value'' and returns a new [[AccessControlLists]] with the added ACLs.
    *
    * @param acls the acls to be added
    */
  def ++(acls: AccessControlLists): AccessControlLists = AccessControlLists(acls.value ++ value)

  /**
    * Adds a key pair of Path and [[AccessControlList]] to the current ''value'' and returns a new [[AccessControlLists]] with the added acl.
    *
    * @param entry the key pair of Path and ACL to be added
    */
  def +(entry: (Path, AccessControlList)): AccessControlLists = AccessControlLists(value + entry)

  /**
    * @return new [[AccessControlLists]] with the same elements as the current one but sorted by [[Path]] (alphabetically)
    */
  def sorted: AccessControlLists = AccessControlLists(ListMap(value.toSeq.sortBy(_._1.repr): _*))

  /**
    * Generates a new [[AccessControlLists]] only containing the provided ''identities''.
    *
    * @param identities the identities to be filtered
    */
  def filter(identities: Set[Identity]): AccessControlLists =
    value.foldLeft(AccessControlLists.empty) {
      case (acc, (p, acl)) => acc + (p -> acl.filter(identities))
    }

  /**
    * @return a new [[AccessControlLists]] containing the ACLs with non empty [[AccessControlList]]
    */
  def removeEmpty: AccessControlLists = AccessControlLists(value.filterNot { case (_, v) => v.hasVoidPermissions })
}

object AccessControlLists {

  /**
    * An empty [[AccessControlLists]].
    */
  val empty: AccessControlLists = AccessControlLists(Map.empty[Path, AccessControlList])

  /**
    * Convenience factory method to build an ACLs from var args of ''Path'' to ''AccessControlList'' tuples.
    */
  final def apply(tuple: (Path, AccessControlList)*): AccessControlLists = AccessControlLists(tuple.toMap)
}
