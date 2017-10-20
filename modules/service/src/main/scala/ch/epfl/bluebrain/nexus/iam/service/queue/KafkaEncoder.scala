package ch.epfl.bluebrain.nexus.iam.service.queue

import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json, Printer}

// $COVERAGE-OFF$
trait KafkaEncoder {

  protected val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)

  protected implicit val config: Configuration =
    Configuration.default.withDiscriminator("type")

  protected implicit val accessControlListEncoder: Encoder[AccessControlList] = Encoder.encodeJson.contramap { acl =>
    acl.acl.asJson
  }

  protected implicit val metaEncoder: Encoder[Meta] = Encoder.encodeJson.contramap { _ =>
    Json.Null
  }

  import Identity._
  protected implicit val identityEncoderWithId: Encoder[Identity] =
    Encoder.encodeJson.contramap(identity => {
      val id = identity match {
        case GroupRef(origin, group)  => Json.obj("@id" -> Json.fromString(s"$origin/$group"))
        case UserRef(origin, subject) => Json.obj("@id" -> Json.fromString(s"$origin/$subject"))
        case _                        => Json.obj()
      }
      identityEncoder.apply(identity) deepMerge id
    })

}
// $COVERAGE-ON$
