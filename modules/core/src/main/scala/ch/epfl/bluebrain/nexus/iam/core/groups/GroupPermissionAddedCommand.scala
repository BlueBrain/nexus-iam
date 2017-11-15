package ch.epfl.bluebrain.nexus.iam.core.groups

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.GroupRef

/**
  * Class representing a command to add a group to a list of used groups
  * @param groupRef group to be added
  */
final case class GroupPermissionAddedCommand(groupRef: GroupRef)
