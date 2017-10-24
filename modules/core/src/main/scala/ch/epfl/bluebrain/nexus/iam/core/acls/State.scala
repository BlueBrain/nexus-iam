package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.commons.iam.acls.Permissions
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity

/**
  * Enumeration type for possible states of a resource on the ACL.
  */
sealed trait State extends Product with Serializable

object State {

  /**
    * Blank state that represents an initial empty permissions mapping.
    */
  sealed trait Initial      extends State
  final case object Initial extends Initial

  /**
    * State instance holding the current identity to permissions ''mapping'' for a specific ''path''.
    *
    * @param path    the path
    * @param mapping the mapping between identities and their respective permissions set
    */
  final case class Current(path: Path, mapping: Map[Identity, Permissions]) extends State
}
