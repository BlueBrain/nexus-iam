package ch.epfl.bluebrain.nexus.iam.service.queue

import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json, Printer}
import akka.http.scaladsl.model.Uri

// $COVERAGE-OFF$
trait KafkaEncoder {

  val baseUri: Uri

  lazy val contextUri = baseUri.withPath(baseUri.path / "context")

  protected implicit val config: Configuration =
    Configuration.default.withDiscriminator("type")

  protected val printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)

  protected implicit val accessControlListEncoder: Encoder[AccessControlList] = Encoder.encodeJson.contramap { acl =>
    acl.acl.asJson
  }

  protected implicit val metaEncoder: Encoder[Meta] = Encoder.encodeJson.contramap { _ =>
    Json.Null
  }

  import Identity._
  protected implicit val identityEncoderWithId: Encoder[Identity] =
    Encoder.encodeJson.contramap(identity => {

      val context = Json.obj("@context" -> Json.fromString(contextUri.toString))
      val id = identity match {
        case GroupRef(origin, group) =>
          Json.obj(
            "@id" -> Json.fromString(
              baseUri.withPath(baseUri.path / "realms" / origin.name / "groups" / group).toString))
        case UserRef(origin, sub) =>
          Json.obj(
            "@id" -> Json.fromString(baseUri.withPath(baseUri.path / "realms" / origin.name / "users" / sub).toString))
        case _ => Json.obj()
      }

      identityEncoder
        .mapJson(json => json.mapObject(obj => obj("type").map(f => obj.add("@type", f).remove("type")).getOrElse(obj)))
        .apply(identity) deepMerge id deepMerge context
    })

}
// $COVERAGE-ON$
