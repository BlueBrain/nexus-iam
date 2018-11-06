package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.service.http.Path

/**
  * Enumeration of ACLs states.
  */
sealed trait AclState extends Product with Serializable

object AclState {

  /**
    * The initial (undefined) state.
    */
  final case object Initial extends AclState

  /**
    * An existing ACLs state.
    *
    * @param path      the target path for the ACL
    * @param acl       the AccessControl collection
    * @param rev       the ACLs revision
    * @param created   the instant when the resource was created
    * @param updated   the instant when the resource was last updated
    * @param createdBy the identity that created the resource
    * @param updatedBy the identity that last updated the resource
    */
  final case class Current(path: Path,
                           acl: AccessControlList,
                           rev: Long,
                           created: Instant,
                           updated: Instant,
                           createdBy: Identity,
                           updatedBy: Identity)
      extends AclState

  def next(state: AclState, ev: AclEvent): AclState = (state, ev) match {

    case (Initial, AclCreated(p, acl, 1L, instant, identity)) =>
      Current(p, acl, 1L, instant, instant, identity, identity)

    case (Initial, _) => Initial

    case (c: Current, _: AclCreated) => c

    case (c: Current, AclUpdated(p, acl, rev, instant, identity)) =>
      c.copy(p, acl, rev, updated = instant, updatedBy = identity)

    case (c: Current, AclAppended(p, acl, rev, instant, identity)) =>
      c.copy(p, c.acl ++ acl, rev, updated = instant, updatedBy = identity)

    case (c: Current, AclSubtracted(p, acl, rev, instant, identity)) =>
      c.copy(p, c.acl -- acl, rev, updated = instant, updatedBy = identity)

    case (c: Current, AclDeleted(p, rev, instant, identity)) =>
      c.copy(p, AccessControlList.empty, rev, updated = instant, updatedBy = identity)

  }
}
