package ch.epfl.bluebrain.nexus.iam.core.groups

import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.GroupRef

/**
  * Event wrapping a group to be added to a list of used groups
  * @param groupRef
  */
final case class GroupPermissionAddedEvent(groupRef: GroupRef)
