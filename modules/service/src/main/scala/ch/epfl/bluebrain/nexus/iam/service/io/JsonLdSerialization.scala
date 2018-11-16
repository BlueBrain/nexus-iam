package ch.epfl.bluebrain.nexus.iam.service.io

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.types.Identity.{AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControl, AccessControlList}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, Printer}
import io.circe.java8.time._

trait ConfigInstance {
  private[io] implicit val config: Configuration =
    Configuration.default
      .withDiscriminator("@type")
      .copy(transformMemberNames = {
        case "id"  => "@id"
        case other => other
      })
}
trait IdentityJsonLdDecoder extends ConfigInstance {

  /**
    * Identity decoder which converts JSON-LD representation to ''Identity''
    */
  def identityDecoder: Decoder[Identity] = {
    import io.circe.generic.extras.semiauto._
    deriveDecoder[Identity]
  }
}

trait EventJsonLdDecoder extends IdentityJsonLdDecoder {

  private implicit def aclDecoder(implicit E: Decoder[List[AccessControl]]): Decoder[AccessControlList] =
    Decoder.decodeHCursor.emap { hc =>
      hc.field("acl")
        .as[List[AccessControl]]
        .fold(f => Left(f.message), acl => Right(AccessControlList(acl.toSet)))
    }

  /**
    * Event decoder which uses `Decoder[Identity]` which maps `@id` JSON field to `id` in the `Identity` data model.
    *
    * @return decoder for `Event`
    */
  def eventDecoder: Decoder[Event] = {
    import io.circe.generic.extras.auto._
    implicit val id: Decoder[Identity]      = identityDecoder
    implicit val metaDecoder: Decoder[Meta] = deriveDecoder[Meta]
    implicitly[Decoder[Event]]
  }
}

trait IdentityJsonLdEncoder extends ConfigInstance {

  /**
    * Identity encoder which adds `@id` field to JSON representation of ''Identity''
    *
    * @param base base URI for the API
    * @return encoder for `Identity`
    */
  def identityEncoder(base: Uri): Encoder[Identity] = {
    def jsonIdOf(identity: Identity): Json = {
      val id = Json.obj("@id" -> Json.fromString(base.withPath(Path(s"${base.path}/${identity.id.show}")).toString))
      val metadata = identity match {
        case u: UserRef  => Json.obj("realm" -> Json.fromString(u.realm), "sub"   -> Json.fromString(u.sub))
        case g: GroupRef => Json.obj("realm" -> Json.fromString(g.realm), "group" -> Json.fromString(g.group))
        case u: AuthenticatedRef =>
          u.realm.map(realm => Json.obj("realm" -> Json.fromString(realm))).getOrElse(Json.obj())
        case _ => Json.obj()
      }
      metadata deepMerge id
    }

    import io.circe.generic.extras.semiauto._
    Encoder.encodeJson.contramap { id =>
      deriveEncoder[Identity].apply(id) deepMerge jsonIdOf(id)
    }
  }
}

trait EventJsonLdEncoder extends IdentityJsonLdEncoder with ConfigInstance {

  private implicit def aclEncoder(implicit E: Encoder[List[AccessControl]]): Encoder[AccessControlList] =
    E.contramap(_.acl.toList)

  /**
    * Event encoder which uses `Encoder[Identity]` which adds `@id` field to JSON representation of `Identity`
    *
    * @param base base URI for the API
    * @return encoder for `Event`
    */
  def eventEncoder(base: Uri): Encoder[Event] = {
    import io.circe.generic.extras.auto._
    implicit val id: Encoder[Identity]      = identityEncoder(base)
    implicit val metaEncoder: Encoder[Meta] = deriveEncoder[Meta]
    implicitly[Encoder[Event]]
  }
}

/**
  * A generic marshaller that marshalls objects of type `A` to `String`
  *
  * @tparam A type of object to be marshalled
  */
trait EventJsonLdMarshaller[A] {
  def apply(a: A): String
}

object JsonLdSerialization extends EventJsonLdEncoder with EventJsonLdDecoder {

  /**
    * Creates a marshaller that uses Circe Encoder[A] and adds @context field
    *
    * @param base    URI for the API
    * @param encoder circe encoder for A
    * @param printer printer to be used when marshalling into `String`
    * @tparam A Class of object to be marshalled
    * @return `EventJsonLdMarshaller` for type `A`
    */
  def jsonLdMarshaller[A](base: Uri)(
      implicit
      encoder: Encoder[A],
      printer: Printer = Printer.noSpaces.copy(dropNullValues = true)): EventJsonLdMarshaller[A] =
    (a: A) => {
      val context = Json.obj(
        "@context" -> Json.fromString(base.copy(path = base.path / "context").toString())
      )
      encoder.apply(a).deepMerge(context).pretty(printer)
    }
}
