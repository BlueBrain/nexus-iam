package ch.epfl.bluebrain.nexus.iam.service.queue

import ch.epfl.bluebrain.nexus.commons.iam.acls.{AccessControl, AccessControlList}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.acls.{Event, Meta}
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.generic.extras.Configuration
import io.circe.{Encoder, Json}

trait KafkaEncoders {

  private implicit val config: Configuration =
    Configuration.default.withDiscriminator("@type")

  private implicit def accessControlListEncoder(implicit E: Encoder[List[AccessControl]]): Encoder[AccessControlList] =
    E.contramap(_.acl.toList)

  private implicit val metaEncoder: Encoder[Meta] =
    Encoder.encodeJson.contramap { _ =>
      Json.Null
    }

  private def kafkaIdentityEncoder(base: ApiUri): Encoder[Identity] = {
    def jsonIdOf(id: Identity): Json = id match {
      case GroupRef(realm, group) =>
        Json.obj(
          "@id" -> Json.fromString(base.base.withPath(base.base.path / "realms" / realm / "groups" / group).toString))
      case UserRef(realm, sub) =>
        Json.obj(
          "@id" -> Json.fromString(base.base.withPath(base.base.path / "realms" / realm / "users" / sub).toString))
      case _ => Json.obj()
    }

    import io.circe.generic.extras.semiauto._
    Encoder.encodeJson.contramap { id =>
      deriveEncoder[Identity].apply(id) deepMerge jsonIdOf(id)
    }
  }

  def eventEncoder(implicit base: ApiUri): Encoder[Event] = {
    import io.circe.generic.extras.auto._
    implicit val id: Encoder[Identity] = kafkaIdentityEncoder(base)
    implicitly[Encoder[Event]]
  }
}

object KafkaEncoders extends KafkaEncoders
