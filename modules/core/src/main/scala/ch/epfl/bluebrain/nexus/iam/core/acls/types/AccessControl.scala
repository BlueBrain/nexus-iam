package ch.epfl.bluebrain.nexus.iam.core.acls.types

import ch.epfl.bluebrain.nexus.iam.types.Identity

/**
  * Type definition that is essentially a pair consisting of an ''identity'' and its associated ''permissions''.
  */
final case class AccessControl(identity: Identity, permissions: Permissions)
