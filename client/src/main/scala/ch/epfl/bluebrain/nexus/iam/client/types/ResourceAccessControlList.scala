package ch.epfl.bluebrain.nexus.iam.client.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.client.config.Contexts._
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import io.circe.syntax._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

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
final case class ResourceAccessControlList(id: AbsoluteIri,
                                           rev: Long,
                                           types: Set[AbsoluteIri],
                                           createdAt: Instant,
                                           createdBy: Subject,
                                           updatedAt: Instant,
                                           updatedBy: Subject,
                                           value: AccessControlList)
object ResourceAccessControlList {

  implicit def resourceAccessControlListEncoder(implicit config: IamClientConfig): Encoder[ResourceAccessControlList] =
    Encoder.encodeJson.contramap {
      case ResourceAccessControlList(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, acl) =>
        Json
          .obj(
            "@id"                -> id.asJson,
            "@type"              -> Json.arr(types.map(t => Json.fromString(t.asString.stripPrefix(nxv.base.asString))).toSeq: _*),
            nxv.rev.prefix       -> Json.fromLong(rev),
            nxv.createdBy.prefix -> createdBy.id.asJson,
            nxv.updatedBy.prefix -> updatedBy.id.asJson,
            nxv.createdAt.prefix -> Json.fromString(createdAt.toString),
            nxv.updatedAt.prefix -> Json.fromString(updatedAt.toString)
          )
          .addContext(resourceCtxUri)
          .addContext(iamCtxUri) deepMerge acl.asJson
    }

  implicit def resourceAccessControlListDecoder(implicit config: IamClientConfig): Decoder[ResourceAccessControlList] =
    Decoder.instance { hc =>
      def toSubject(id: AbsoluteIri): Decoder.Result[Subject] =
        Identity(id)
          .collect { case s: Subject => s }
          .toRight(DecodingFailure(s"wrong subject with id '${id.asString}'", hc.history))
      for {
        id       <- hc.get[AbsoluteIri]("@id")
        typesStr <- hc.get[Vector[String]]("@type")
        types = typesStr.map(tpe => Iri.absolute(tpe).getOrElse(nxv.base + tpe))
        rev       <- hc.get[Long](nxv.rev.prefix)
        createdBy <- hc.get[AbsoluteIri](nxv.createdBy.prefix).flatMap(toSubject)
        updatedBy <- hc.get[AbsoluteIri](nxv.updatedBy.prefix).flatMap(toSubject)
        createdAt <- hc.get[Instant](nxv.createdAt.prefix)
        updatedAt <- hc.get[Instant](nxv.updatedAt.prefix)
        acl       <- hc.value.as[AccessControlList]
      } yield ResourceAccessControlList(id, rev, types.toSet, createdAt, createdBy, updatedAt, updatedBy, acl)
    }
}
