package ch.epfl.bluebrain.nexus.iam.service.types

import ch.epfl.bluebrain.nexus.iam.core.acls.Permissions
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity

final case class AccessControlList(acl: List[AccessControl])

object AccessControlList {
  def apply(acl: (Identity, Permissions)*): AccessControlList =
    new AccessControlList(acl.map {
      case (id, ps) => AccessControl(id, ps)
    }.toList)
}
