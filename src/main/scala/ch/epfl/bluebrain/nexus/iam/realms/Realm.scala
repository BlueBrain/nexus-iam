package ch.epfl.bluebrain.nexus.iam.realms
import ch.epfl.bluebrain.nexus.rdf.Iri.Url

case class Realm(label: String, openidConfiguration: Url, requiredScopes: Set[String])
