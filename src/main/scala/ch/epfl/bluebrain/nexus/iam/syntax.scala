package ch.epfl.bluebrain.nexus.iam

import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.types.Identity._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

object syntax {

  final implicit def identitiesSyntax(identities: Set[Identity]): IdentitiesSyntax = new IdentitiesSyntax(identities)
  final implicit def absoluteIriSyntax(iri: AbsoluteIri): AbsoluteIriSyntax        = new AbsoluteIriSyntax(iri)

  final class IdentitiesSyntax(private val identities: Set[Identity]) extends AnyVal {
    private def findUser: Option[User]      = identities.collectFirst { case user: User      => user }
    private def findAnon: Option[Anonymous] = identities.collectFirst { case anon: Anonymous => anon }

    /**
      * Attempts to fetch the subject from the ''identities''. The subject is the first ''User'' found or the ''Anonymous'' identity.
      *
      * @return Some(identity) when the subject is contained in the ''identities'', None otherwise
      */
    def subject: Option[Identity] = findUser orElse findAnon
  }

  final class AbsoluteIriSyntax(private val iri: AbsoluteIri) extends AnyVal {
    def lastSegment: Option[String] =
      iri.path.head match {
        case segment: String => Some(segment)
        case _               => None
      }
  }

}
