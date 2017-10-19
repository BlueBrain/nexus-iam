package ch.epfl.bluebrain.nexus.iam.service.queue

import ch.epfl.bluebrain.nexus.iam.core.acls._
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

}
// $COVERAGE-ON$
