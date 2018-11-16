package ch.epfl.bluebrain.nexus.iam.elastic.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permissions
import ch.epfl.bluebrain.nexus.service.http.Path

/**
  * Type definition representing the ACLs for a list of resources.
  *
  * @param acl a list of [[FullAccessControl]]
  */
final case class FullAccessControlList(acl: List[FullAccessControl])

object FullAccessControlList {
  // $COVERAGE-OFF$

  /**
    * Convenience factory method to build an ACL from var args of ''Identity'' to ''Permissions'' tuples.
    */
  def apply(acl: (Identity, Path, Permissions)*): FullAccessControlList =
    new FullAccessControlList(acl.map { case (id, path, ps) => FullAccessControl(id, path, ps) }.toList)

  // $COVERAGE-ON$
}
