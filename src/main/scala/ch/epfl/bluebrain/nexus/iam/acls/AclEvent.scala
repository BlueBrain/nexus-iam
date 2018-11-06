package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.service.http.Path

/**
  * Enumeration of ACL event types.
  */
sealed trait AclEvent extends Product with Serializable {

  /**
    * @return the target path for the ACL
    */
  def path: Path

  /**
    * @return the revision that this event generated
    */
  def rev: Long

  /**
    * @return the instant when this event was created
    */
  def instant: Instant

  /**
    * @return the identity which created this event
    */
  def identity: Identity

}

object AclEvent {

  /**
    * A witness to ACL creation.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL created, represented as a mapping of identities to permissions
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class AclCreated(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclEvent

  /**
    * A witness to ACL update.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL updated, represented as a mapping of identities to permissions
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class AclUpdated(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclEvent

  /**
    * A witness to ACL append.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL appended, represented as a mapping of identities to permissions
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class AclAppended(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclEvent

  /**
    * A witness to ACL subtraction.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL subtracted, represented as a mapping of identities to permissions
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class AclSubtracted(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclEvent

  /**
    * A witness to ACL deletion.
    *
    * @param path     the target path for the ACL
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class AclDeleted(path: Path, rev: Long, instant: Instant, identity: Identity) extends AclEvent
}
