package ch.epfl.bluebrain.nexus.iam.acls

import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.rdf.Iri.Path

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class AclRejection(val msg: String) extends Product with Serializable

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object AclRejection {

  /**
    * Signals an attempt to append/subtract ACLs that won't change the current state.
    *
    * @param path the target path for the ACL
    */
  final case class NothingToBeUpdated(path: Path)
      extends AclRejection(s"The ACL on path '$path' will not change after applying the provided update.")

  /**
    * Signals an attempt to modify ACLs that do not exists.
    *
    * @param path the target path for the ACL
    */
  final case class AclNotFound(path: Path) extends AclRejection(s"The ACL on path '$path' does not exists.")

  /**
    * Signals an attempt to delete ACLs that are already empty.
    *
    * @param path the target path for the ACL
    */
  final case class AclIsEmpty(path: Path) extends AclRejection(s"The ACL on path '$path' is empty.")

  /**
    * Signals an attempt to interact with an ACL collection with an incorrect revision.
    *
    * @param path the target path for the ACL
    * @param rev the revision provided
    */
  final case class IncorrectRev(path: Path, rev: Long)
      extends AclRejection(s"ACL on path '$path' with incorrect revision '$rev' provided.")

  /**
    * Signals an attempt to create/replace/append/subtract ACL collection which contains void permissions.
    *
    * @param path the target path for the ACL
    */
  final case class AclCannotContainEmptyPermissionCollection(path: Path)
      extends AclRejection(s"The ACL for path '$path' cannot contain an empty permission collection.")

  /**
    * Signals that an acl operation could not be performed because of unknown referenced permissions.
    *
    * @param permissions the unknown permissions
    */
  final case class UnknownPermissions(permissions: Set[Permission])
      extends AclRejection(
        s"Some of the permissions specified are not known: '${permissions.mkString("\"", ", ", "\"")}'")
}
