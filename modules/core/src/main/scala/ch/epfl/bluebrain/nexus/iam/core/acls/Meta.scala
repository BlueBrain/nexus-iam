package ch.epfl.bluebrain.nexus.iam.core.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.core.identity.Identity

/**
  * Type definition that aggregates ''author'' and ''timestamp'' as meta information for
  * [[ch.epfl.bluebrain.nexus.iam.core.acls.Command]]s and [[ch.epfl.bluebrain.nexus.iam.core.acls.Event]]s.
  *
  * @param author  the origin of the Command or Event
  * @param instant the moment in time when the Command or Event was emitted
  */
final case class Meta(author: Identity, instant: Instant)
