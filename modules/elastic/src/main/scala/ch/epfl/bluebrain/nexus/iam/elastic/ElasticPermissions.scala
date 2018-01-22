package ch.epfl.bluebrain.nexus.iam.elastic

import ch.epfl.bluebrain.nexus.commons.iam.acls.{Path, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

/**
  *
  * @param path        the path of the permissions
  * @param pathDepth   the depth of the path (amount of nested levels)
  * @param identity    the identity to which the permissions apply
  * @param permissions the permissions
  */
final case class ElasticPermissions(path: Path, pathDepth: Int, identity: Identity, permissions: Permissions)

object ElasticPermissions {

  /**
    * Construct an [[ElasticPermissions]]
    *
    * @param path        the path of the permissions
    * @param identity    the identity to which the permissions apply
    * @param permissions the permissions
    */
  final def apply(path: Path, identity: Identity, permissions: Permissions): ElasticPermissions =
    ElasticPermissions(path, path.length, identity, permissions)
}
