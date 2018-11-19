package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.types.{ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path

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
    def toResource(implicit http: HttpConfig): ResourceAccessControlList =
      ResourceF(base + path.asString, rev, types, createdAt, createdBy, updatedAt, updatedBy, acl)

    def toResourceMetadata(implicit http: HttpConfig): ResourceMetadata =
      ResourceMetadata(base + path.asString, rev, types, createdAt, createdBy, updatedAt, updatedBy)
  }

}
