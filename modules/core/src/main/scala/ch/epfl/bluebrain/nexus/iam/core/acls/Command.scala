package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.service.http.Path

/**
  * Base enumeration type for __command__ definitions.  Commands are attempts to change the system internal state, not
  * necessarily successful.  Command execution may have side effects and result in one or multiple
  * [[ch.epfl.bluebrain.nexus.iam.core.acls.Event]]s.
  *
  * @see [[ch.epfl.bluebrain.nexus.iam.core.acls.Event]]
  */
sealed trait Command extends Product with Serializable {

  /**
    * @return the path on which this command applies
    */
  def path: Path

  /**
    * @return the metadata associated to this command
    */
  def meta: Meta
}

object Command {

  /**
    * Command definition signifying an attempt to add the argument ''mapping'' of permissions for one or several
    * identities on a ''path''.
    *
    * @param path        the path on which the permissions should change
    * @param acl         the list of pairs of identities and their respective permissions to be created
    * @param meta        the command metadata
    */
  final case class AddPermissions(path: Path, acl: AccessControlList, meta: Meta) extends Command

  /**
    * Command definition signifying an attempt to subtract the argument ''permissions'' from the current collection
    * of permissions for ''identity'' on ''path''.
    *
    * @param path        the path on which the permissions should change
    * @param identity    the identity for which the permissions should change
    * @param permissions the permissions to be subtracted
    * @param meta        the command metadata
    */
  final case class SubtractPermissions(path: Path, identity: Identity, permissions: Permissions, meta: Meta)
      extends Command

  /**
    * Command definition signifying an attempt to remove the entire set of permissions for ''identity'' on ''path''.
    *
    * @param path     the path on which the permissions should change
    * @param identity the identity for which the permissions should change
    * @param meta     the command metadata
    */
  final case class RemovePermissions(path: Path, identity: Identity, meta: Meta) extends Command

  /**
    * Command definition signifying an attempt to clear the entire set of permissions for all identities on ''path''.
    *
    * @param path     the path on which the permissions should change
    * @param meta     the command metadata
    */
  final case class ClearPermissions(path: Path, meta: Meta) extends Command

}
