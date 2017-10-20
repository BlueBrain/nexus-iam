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
  protected implicit val identityEncoderWithId: Encoder[Identity] = identityEncoder.mapJson { json: Json =>
    val idOption = for {
      origin  <- fieldAsString(json, "origin")
      subject <- fieldAsString(json, "subject") orElse fieldAsString(json, "group")
    } yield s"$origin/$subject"
    idOption match {
      case Some(id) => json.asObject.map(_.add("@id", Json.fromString(id)).asJson).getOrElse(json)
      case None     => json
    }
  }

  private def fieldAsString(json: Json, field: String): Option[String] =
    json.asObject.flatMap(_(field)).flatMap(_.asString)

}
// $COVERAGE-ON$
