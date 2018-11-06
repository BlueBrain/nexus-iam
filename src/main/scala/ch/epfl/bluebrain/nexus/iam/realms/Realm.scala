package ch.epfl.bluebrain.nexus.iam.realms
import ch.epfl.bluebrain.nexus.rdf.Iri.Url

/**
  * Representation of an authentication realm.
  *
  * @param label                label of the realm
  * @param openidConfiguration  URL of the realm's openID configuration endpoint
  * @param requiredScopes       scopes required for the access token from this realm to be accepted
  */
final case class Realm(label: String, openidConfiguration: Url, requiredScopes: Set[String])
