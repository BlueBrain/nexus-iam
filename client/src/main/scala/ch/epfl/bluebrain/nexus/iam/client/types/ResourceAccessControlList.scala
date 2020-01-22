package ch.epfl.bluebrain.nexus.iam.client.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.client.config.Contexts._
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.implicits._
import io.circe._
import io.circe.syntax._

/**
  * The Access Control List with the metadata information
  *
  * @param id        the id of the resource
  * @param rev       the revision of the resource
  * @param types     the types of the resource
  * @param createdAt the creation date of the resource
  * @param createdBy the subject that created the resource
  * @param updatedAt the last update date of the resource
  * @param updatedBy the subject that performed the last update to the resource
  * @param value     the Access Control List
  */
final case class ResourceAccessControlList(
    id: AbsoluteIri,
    rev: Long,
    types: Set[AbsoluteIri],
    createdAt: Instant,
    createdBy: Subject,
    updatedAt: Instant,
    updatedBy: Subject,
    value: AccessControlList
)
object ResourceAccessControlList {

  implicit def resourceAccessControlListEncoder(implicit config: IamClientConfig): Encoder[ResourceAccessControlList] =
    Encoder.encodeJson.contramap {
      case ResourceAccessControlList(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, acl) =>
        val jsonTypes = types.toList match {
          case Nil      => Json.Null
          case t :: Nil => Json.fromString(t.lastSegment.getOrElse(t.asString))
          case _        => Json.arr(types.map(t => Json.fromString(t.lastSegment.getOrElse(t.asString))).toSeq: _*)
        }
        Json
          .obj(
            "@id"                -> id.asJson,
            "@type"              -> jsonTypes,
            nxv.rev.prefix       -> Json.fromLong(rev),
            nxv.createdBy.prefix -> createdBy.id.asJson,
            nxv.updatedBy.prefix -> updatedBy.id.asJson,
            nxv.createdAt.prefix -> Json.fromString(createdAt.toString),
            nxv.updatedAt.prefix -> Json.fromString(updatedAt.toString)
          )
          .addContext(resourceCtxUri)
          .addContext(iamCtxUri) deepMerge acl.asJson
    }

  implicit def resourceAccessControlListDecoder: Decoder[ResourceAccessControlList] =
    Decoder.instance { hc =>
      def toSubject(id: AbsoluteIri): Decoder.Result[Subject] =
        Identity(id)
          .collect { case s: Subject => s }
          .toRight(DecodingFailure(s"wrong subject with id '${id.asString}'", hc.history))
      def decodeTypes(cursor: HCursor): Decoder.Result[Set[AbsoluteIri]] =
        cursor
          .get[Set[String]]("@type")
          .orElse(cursor.get[String]("@type").map(str => Set(str)))
          .orElse(Right(Set.empty))
          .map(types => types.map(tpe => Iri.absolute(tpe).getOrElse(nxv.base + tpe)))
      for {
        id        <- hc.get[AbsoluteIri]("@id")
        types     <- decodeTypes(hc)
        rev       <- hc.get[Long](nxv.rev.prefix)
        createdBy <- hc.get[AbsoluteIri](nxv.createdBy.prefix).flatMap(toSubject)
        updatedBy <- hc.get[AbsoluteIri](nxv.updatedBy.prefix).flatMap(toSubject)
        createdAt <- hc.get[Instant](nxv.createdAt.prefix)
        updatedAt <- hc.get[Instant](nxv.updatedAt.prefix)
        acl       <- hc.value.as[AccessControlList]
      } yield ResourceAccessControlList(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, acl)
    }

  private[ResourceAccessControlList] implicit class AbsoluteIriSyntax(private val iri: AbsoluteIri) extends AnyVal {
    def lastSegment: Option[String] =
      iri.path.head match {
        case segment: String => Some(segment)
        case _               => None
      }
  }
}
