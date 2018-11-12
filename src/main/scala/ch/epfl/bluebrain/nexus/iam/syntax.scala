package ch.epfl.bluebrain.nexus.iam

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, UserRef}

object syntax {

  final implicit def identitiesSyntax(identities: Set[Identity]): IdentitiesSyntax = new IdentitiesSyntax(identities)

  final class IdentitiesSyntax(private val identities: Set[Identity]) extends AnyVal {
    private def findUser: Option[UserRef]   = identities.collectFirst { case user: UserRef   => user }
    private def findAnon: Option[Anonymous] = identities.collectFirst { case anon: Anonymous => anon }

    /**
      * Attempts to fetch the subject from the ''identities''. The subject is the first ''UserRef'' found or the ''Anonymous'' identity.
      *
      * @return Some(identity) when the subject is contained in the ''identities'', None otherwise
      */
    def subject: Option[Identity] = findUser orElse findAnon
  }

}
