package ch.epfl.bluebrain.nexus.iam.core.acls

import cats.kernel.Semigroup
import io.circe.{Decoder, Encoder}

/**
  * Type definition that represents a set of permissions.
  *
  * @param set the set of [[ch.epfl.bluebrain.nexus.iam.core.acls.Permission]]s
  */
final case class Permissions(set: Set[Permission]) extends (Permission => Boolean) {

  /**
    * Adds the argument __Permission__ to this set.  If the argument permission is already included in the
    * current set, the function will return a copy of __this__.
    *
    * @param perm the permission to add to this set
    * @return a new __Permissions__ instance containing both current and argument permissions
    */
  def +(perm: Permission): Permissions = Permissions(set + perm)

  /**
    * Subtracts the argument __Permission__ from this set.  If the argument permission is not included in the
    * current set, the function will return a copy of __this__.
    *
    * @param perm the permission to subtract from this set
    * @return a new __Permissions__ instance containing all of the permissions of this set except the argument
    *         permission
    */
  def -(perm: Permission): Permissions = Permissions(set - perm)

  /**
    * Adds the argument __Permissions__ to this set.
    *
    * @param perms the permissions to add to this set
    * @return a new __Permissions__ instance containing the reunion of permissions of the current and argument sets
    */
  def ++(perms: Permissions): Permissions = Permissions(set ++ perms.set)

  /**
    * Subtracts the argument __Permissions__ from this set.  If the current and argument sets are disjoint the
    * function will return a copy of __this__.
    *
    * @param perms the permissions to subtract from this set
    * @return a new __Permissions__ instance containing all of the permissions of this set except the permissions
    *         defined in the argument set
    */
  def --(perms: Permissions): Permissions = Permissions(set -- perms.set)

  /**
    * The intersection of this and perms.
    * @param perms a set of permissions to intersect against
    * @return the Permissions which intersect with this and perms
    */
  def intersect(perms: Permissions): Permissions = Permissions(set intersect perms.set)

  /**
    * The intersection of this and perms.
    * @param perms a set of permissions to intersect against
    * @return the Permissions which intersect with this and perms
    */
  def &(perms: Permissions): Permissions = intersect(perms)

  /**
    * Checks whether the argument __Permission__ is included in this set.
    *
    * @param permission the permission to check for inclusion in this set
    * @return __true__ if the argument permission is included in this set, __false__ otherwise
    */
  def contains(permission: Permission): Boolean = set.contains(permission)

  /**
    * Checks whether the argument __Permission__ is included in this set.  This method is equivalent to
    * `contains`. It allows permissions sets to be interpreted as predicates.
    *
    * @param permission the permission to test for inclusion
    * @return __true__ if the argument permission is included in this set, __false__ otherwise
    */
  override def apply(permission: Permission): Boolean = set.contains(permission)

  /**
    * Checks whether this set includes no permissions (is empty).
    *
    * @return __true__ if this set contains no permissions, __false__ otherwise
    */
  def isEmpty: Boolean = set.isEmpty

}

object Permissions {

  /**
    * Convenience factory method to construct a [[ch.epfl.bluebrain.nexus.iam.core.acls.Permissions]] instance from var args.
    *
    * @param items a non-empty permissions set to be included in the resulting instance
    * @return a new __Permissions__ instance
    */
  def apply(items: Permission*): Permissions = apply(items.toSet)

  /**
    * Singleton representing the empty permissions set.
    */
  val empty: Permissions = new Permissions(Set.empty)

  /**
    * @return Semigroup typeclass instance for Permissions
    */
  implicit def semigroupInstance: Semigroup[Permissions] = (x: Permissions, y: Permissions) => x ++ y

  implicit val permissionsEncoder: Encoder[Permissions] = Encoder.encodeSet[Permission].contramap[Permissions](_.set)

  implicit val permissionsDecoder: Decoder[Permissions] = Decoder.decodeSet[Permission].emap(p => Right(Permissions(p)))
}
