package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.commons.types._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path

/**
  * Base enumeration type for __event__ definitions.  Events are proofs that the system has changed its internal state.
  * Replaying a series of events should have no side effects.  Event replay typically happens when there's a need to
  * recompute the full or partial state of the system.
  *
  */
sealed trait Event extends Product with Serializable {

  /**
    * @return the path on which this event applies
    */
  def path: Path

  /**
    * @return the metadata associated to this event
    */
  def meta: Meta
}

object Event {

  /**
    * Event definition signifying a full deletion of permissions for all identities on ''path''.
    *
    * @param path        the path on which the permissions were cleared
    * @param meta        the event metadata
    */
  final case class PermissionsCleared(path: Path, meta: Meta) extends Event

  /**
    * Event definition signifying a removal of all permissions for ''identity'' on ''path''.
    *
    * @param path        the path on which the permissions were removed
    * @param identity    the identity for which the permissions were removed
    * @param meta        the event metadata
    */
  final case class PermissionsRemoved(path: Path, identity: Identity, meta: Meta) extends Event

  /**
    * Event definition signifying a creation or addition of permissions on ''path''.
    *
    * @param path        the path on which the permissions have been created
    * @param acl         the list of pairs of identities and permissions
    * @param meta        the event metadata
    */
  final case class PermissionsAdded(path: Path, acl: AccessControlList, meta: Meta) extends Event

  /**
    * Event definition signifying a subtraction patch of permissions for ''identity'' on ''path''.
    *
    * @param path        the path on which the permissions have been subtracted
    * @param identity    the identity for which the permissions have been subtracted
    * @param permissions the newly subtracted permissions
    * @param meta        the event metadata
    */
  final case class PermissionsSubtracted(path: Path, identity: Identity, permissions: Permissions, meta: Meta)
      extends Event

}
