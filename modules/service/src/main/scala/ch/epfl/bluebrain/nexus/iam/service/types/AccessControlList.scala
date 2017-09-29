package ch.epfl.bluebrain.nexus.iam.service.types

import ch.epfl.bluebrain.nexus.iam.core.acls.Permissions
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity

import scala.collection.breakOut

final case class AccessControlList(acl: List[AccessControl]) {

  def toMap: Map[Identity, Permissions] = acl.map { case AccessControl(id, ps) => id -> ps }(breakOut)
}

object AccessControlList {

  def apply(acl: (Identity, Permissions)*): AccessControlList =
    new AccessControlList(acl.map { case (id, ps) => AccessControl(id, ps) }.toList)
}
