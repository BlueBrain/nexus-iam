package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.iam.core.identity.Identity

/**
  * Type definition that is essentially a pair consisting of an ''identity'' and its associated ''permissions''.
  */
final case class AccessControl(identity: Identity, permissions: Permissions)
