package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.types.{GrantType, Label}
import ch.epfl.bluebrain.nexus.iam.types.GrantType.Camel._
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import com.nimbusds.jose.jwk.{JWK, JWKSet}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.{Encoder, Json}

import scala.util.Try

/**
  * An active realm representation.
  *
  * @param id           the label of the realm
  * @param name         the name of the realm
  * @param openIdConfig the address of the openid configuration
  * @param issuer       an identifier for the issuer
  * @param grantTypes   the supported grant types of the realm
  * @param logo         an optional logo address
  * @param keys         the set of JWK keys as specified by rfc 7517 (https://tools.ietf.org/html/rfc7517)
  */
final case class ActiveRealm(
    id: Label,
    name: String,
    openIdConfig: Url,
    issuer: String,
    grantTypes: Set[GrantType],
    logo: Option[Url],
    keys: Set[Json]
) {

  private[realms] lazy val keySet: JWKSet = {
    val jwks = keys.foldLeft(Set.empty[JWK]) {
      case (acc, e) => Try(JWK.parse(e.noSpaces)).map(acc + _).getOrElse(acc)
    }
    import scala.collection.JavaConverters._
    new JWKSet(jwks.toList.asJava)
  }
}

object ActiveRealm {
  implicit val activeEncoder: Encoder[ActiveRealm] =
    deriveEncoder[ActiveRealm].mapJson(json => json.removeKeys("keys", "id"))
}
