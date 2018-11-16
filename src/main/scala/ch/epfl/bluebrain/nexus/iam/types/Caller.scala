package ch.epfl.bluebrain.nexus.iam.types
import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject

/**
  * The client caller. It contains the subject and the list of identities (which contains the subject again)
  *
  * @param subject    the identity that performed the call
  * @param identities the set of other identities associated to the ''subject''. E.g.: groups, anonymous, authenticated
  */
final case class Caller(subject: Subject, identities: Set[Identity])
