package ch.epfl.bluebrain.nexus.iam.types
import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject

final case class Caller(subject: Subject, identities: Set[Identity])
