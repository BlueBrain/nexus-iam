package ch.epfl.bluebrain.nexus.iam.core.auth

import ch.epfl.bluebrain.nexus.iam.core.identity.Identity

/**
  * Base enumeration type for the internal user representation.
  */
sealed trait User extends Product with Serializable {
  def identities: Set[Identity]
}

/**
  * Type holding the entire set of ''identities'' that an authenticated user belongs to.
  */
final case class AuthenticatedUser(identities: Set[Identity]) extends User

/**
  * Singleton representing any unauthenticated user.
  */
case object AnonymousUser extends User {
  override val identities = Set(Identity.Anonymous)
}
