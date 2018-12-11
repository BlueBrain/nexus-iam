package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.iam.types.Label
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import _root_.io.circe.Encoder
import _root_.io.circe.generic.semiauto._

/**
  * A realm representation that has been deprecated.
  *
  * @param id           the label of the realm
  * @param name         the name of the realm
  * @param openIdConfig the address of the openid configuration
  * @param logo         an optional logo address
  */
final case class DeprecatedRealm(
    id: Label,
    name: String,
    openIdConfig: Url,
    logo: Option[Url]
)

object DeprecatedRealm {
  implicit val deprecatedEncoder: Encoder[DeprecatedRealm] = deriveEncoder
}
