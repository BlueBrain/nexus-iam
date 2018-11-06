package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.service.http.Path

/**
  * Enumeration of ACL collection command types.
  */
sealed trait AclCommand extends Product with Serializable {

  /**
    * @return the target path for the ACL
    */
  def path: Path

  /**
    * @return the last known revision of the resource when this command was created
    */
  def rev: Long

  /**
    * @return the instant when this command was created
    */
  def instant: Instant

  /**
    * @return the identity which created this command
    */
  def identity: Identity

}

object AclCommand {

  /**
    * An intent to replace ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be replaced, represented as a mapping of identities to permissions
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class ReplaceAcl(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to append ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be appended, represented as a mapping of identities to permissions
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class AppendAcl(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to subtract ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be subtracted, represented as a mapping of identities to permissions
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class SubtractAcl(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to delete ACL.
    *
    * @param path     the target path for the ACL
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class DeleteAcl(path: Path, rev: Long, instant: Instant, identity: Identity) extends AclCommand
}
