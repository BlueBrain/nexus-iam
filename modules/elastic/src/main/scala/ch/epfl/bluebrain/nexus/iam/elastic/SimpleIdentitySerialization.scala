package ch.epfl.bluebrain.nexus.iam.elastic

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.elastic.SimpleIdentitySerialization.SimpleIdentity._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

object SimpleIdentitySerialization {

  /**
    * Provides a data model for having an alternative decoder of [[Identity]]
    */
  private[elastic] sealed trait SimpleIdentity extends Product with Serializable

  private[elastic] object SimpleIdentity {
    final case class UserRef(realm: String, sub: String)     extends SimpleIdentity
    final case class GroupRef(realm: String, group: String)  extends SimpleIdentity
    final case class AuthenticatedRef(realm: Option[String]) extends SimpleIdentity
    final case object Anonymous                              extends SimpleIdentity

    private implicit val config: Configuration                      = Configuration.default.withDiscriminator("@type")
    private[elastic] val simpleIdentityDec: Decoder[SimpleIdentity] = deriveDecoder[SimpleIdentity]
    private[elastic] val simpleIdentityEnc: Encoder[SimpleIdentity] = deriveEncoder[SimpleIdentity]
  }

  implicit val identityDecoder: Decoder[Identity] = {
    Decoder.decodeHCursor.emap[Identity] { hc =>
      SimpleIdentity.simpleIdentityDec
        .apply(hc)
        .map {
          case GroupRef(realm, group)       => Identity.GroupRef(realm, group)
          case UserRef(realm, sub)          => Identity.UserRef(realm, sub)
          case AuthenticatedRef(maybeRealm) => Identity.AuthenticatedRef(maybeRealm)
          case Anonymous                    => Identity.Anonymous()
        }
        .left
        .map(err => s"Could not find a proper decoder for Identity '${hc.focus}' with error '${err.message}'")
    }
  }

  implicit val identityEncoder: Encoder[Identity] = {
    Encoder.encodeJson.contramap { identity =>
      simpleIdentityEnc(identity match {
        case id: Identity.GroupRef         => GroupRef(id.realm, id.group)
        case id: Identity.UserRef          => UserRef(id.realm, id.sub)
        case id: Identity.AuthenticatedRef => AuthenticatedRef(id.realm)
        case Identity.Anonymous(_)         => Anonymous
      })

    }
  }
}
