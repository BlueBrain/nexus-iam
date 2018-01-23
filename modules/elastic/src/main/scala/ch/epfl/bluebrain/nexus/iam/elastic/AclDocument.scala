package ch.epfl.bluebrain.nexus.iam.elastic

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.iam.acls.{Path, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

/**
  *
  * @param path        the path of the permissions
  * @param pathDepth   the depth of the path (amount of nested levels)
  * @param identity    the identity to which the permissions apply
  * @param permissions the permissions
  * @param created     the optional date information of the creation time
  * @param updated     the date information of the updated time
  **/
final case class AclDocument(path: Path,
                             pathDepth: Int,
                             identity: Identity,
                             permissions: Permissions,
                             created: Option[Instant],
                             updated: Instant)

object AclDocument {

  /**
    * Construct an [[AclDocument]]
    *
    * @param path        the path of the permissions
    * @param identity    the identity to which the permissions apply
    * @param permissions the permissions
    * @param updated     the date information of the updated time
    **/
  final def apply(path: Path, identity: Identity, permissions: Permissions, updated: Instant): AclDocument =
    AclDocument(path, path.length, identity, permissions, None, updated)

  /**
    * Construct an [[AclDocument]]
    *
    * @param path        the path of the permissions
    * @param identity    the identity to which the permissions apply
    * @param permissions the permissions
    * @param created     the date information of the created time
    * @param updated     the date information of the updated time
    **/
  final def apply(path: Path,
                  identity: Identity,
                  permissions: Permissions,
                  created: Instant,
                  updated: Instant): AclDocument =
    AclDocument(path, path.length, identity, permissions, Some(created), updated)
}
