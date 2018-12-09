package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.rdf.Iri.Url

/**
  * A realm representation that has been deprecated.
  *
  * @param name         the name of the realm
  * @param openIdConfig the address of the openid configuration
  * @param logo         an optional logo address
  */
final case class DeprecatedRealm(
    name: String,
    openIdConfig: Url,
    logo: Option[Url]
)
