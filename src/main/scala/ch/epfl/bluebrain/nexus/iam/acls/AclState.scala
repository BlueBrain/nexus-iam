package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.types.{ResourceF, ResourceMetadata}
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
    * @param createdAt the instant when the resource was created
    * @param updatedAt the instant when the resource was last updated
    * @param createdBy the identity that created the resource
    * @param updatedBy the identity that last updated the resource
    */
  final case class Current(path: Path,
                           acl: AccessControlList,
                           rev: Long,
                           createdAt: Instant,
                           updatedAt: Instant,
                           createdBy: Subject,
                           updatedBy: Subject)
      extends AclState {
    lazy val toResource: ResourceAccessControlList =
      ResourceF(base + path.repr, rev, types, createdAt, createdBy, updatedAt, updatedBy, acl)

    lazy val toResourceMetadata: ResourceMetadata =
      ResourceMetadata(base + path.repr, rev, types, createdAt, createdBy, updatedAt, updatedBy)
  }

}
