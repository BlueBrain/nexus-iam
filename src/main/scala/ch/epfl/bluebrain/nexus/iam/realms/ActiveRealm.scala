package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.iam.types.GrantType
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import io.circe.Json

/**
  * An active realm representation.
  *
  * @param name         the name of realm
  * @param openIdConfig the address of the openid configuration
  * @param issuer       an identifier for the issuer
  * @param grantTypes   the supported grant types of the realm
  * @param logo         an optional logo address
  * @param keys         the set of JWK keys as specified by rfc 7517 (https://tools.ietf.org/html/rfc7517)
  */
final case class ActiveRealm(
    name: String,
    openIdConfig: Url,
    issuer: String,
    grantTypes: Set[GrantType],
    logo: Option[Url],
    keys: Set[Json]
)