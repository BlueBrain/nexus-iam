package ch.epfl.bluebrain.nexus.iam.acls

import ch.epfl.bluebrain.nexus.service.http.Path

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class AclRejection(val msg: String) extends Product with Serializable

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object AclRejection {

  /**
    * Signals an attempt to create ACLs that already exists.
    *
    * @param path the target path for the ACL
    */
  final case class AclAlreadyExists(path: Path) extends AclRejection(s"ACL already exists on path '$path'")

  /**
    * Signals an attempt to modify ACLs that do not exists.
    *
    * @param path the target path for the ACL
    */
  final case class AclNotFound(path: Path) extends AclRejection(s"ACL on path '$path' do not exists.")

  /**
    * Signals an attempt to delete ACLs that are already empty.
    *
    * @param path the target path for the ACL
    */
  final case class AclIsEmpty(path: Path) extends AclRejection(s"ACL on path '$path' is empty.")

  /**
    * Signals an attempt to interact with an ACL collection with an incorrect revision.
    *
    * @param path the target path for the ACL
    * @param rev the revision provided
    */
  final case class AclIncorrectRev(path: Path, rev: Long)
      extends AclRejection(s"ACL on path '$path' with incorrect revision '$rev' provided.")

  /**
    * Signals an attempt to write ACL on a path where you don't have permissions.
    *
    * @param path the target path for the ACL
    */
  final case class AclUnauthorizedWrite(path: Path)
      extends AclRejection(s"You don't have permissions to write ACL on path '$path'.")

  /**
    * Signals an attempt to create/replace/append/subtract ACL collection which contains void permissions.
    *
    * @param path the target path for the ACL
    */
  final case class AclInvalidEmptyPermissions(path: Path)
      extends AclRejection(s"ACL on path '$path' cannot contain void permissions.")

}
