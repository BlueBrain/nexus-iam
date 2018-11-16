package ch.epfl.bluebrain.nexus.iam.types
import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.config.Contexts._
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.syntax._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import io.circe.syntax._
import io.circe.{Encoder, Json}

/**
  * The metadata information for any resource in the service
  *
  * @param id        the id of the resource
  * @param rev       the revision
  * @param types     the types of the resource
  * @param createdAt the creation date of the resource
  * @param createdBy the identity that created the resource
  * @param updatedAt the last update date of the resource
  * @param updatedBy the identity that performed the last update to the resource
  * @param value     the resource value
  */
final case class ResourceF[A](
    id: AbsoluteIri,
    rev: Long,
    types: Set[AbsoluteIri],
    createdAt: Instant,
    createdBy: Identity,
    updatedAt: Instant,
    updatedBy: Identity,
    value: A
) {

  def map[B](f: A => B): ResourceF[B] =
    copy(value = f(value))

  def discard: ResourceF[Unit] =
    map(_ => ())
}

object ResourceF {
  def unit(
      id: AbsoluteIri,
      rev: Long,
      types: Set[AbsoluteIri],
      createdAt: Instant,
      createdBy: Identity,
      updatedAt: Instant,
      updatedBy: Identity
  ): ResourceF[Unit] =
    ResourceF(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, ())

  implicit def resourceMetaEncoder(implicit http: HttpConfig): Encoder[ResourceMetadata] =
    Encoder.encodeJson.contramap {
      case ResourceF(id, rev, types, createdAt, createdBy, updatedAt, updatedBy, _: Unit) =>
        Json.obj(
          "@context"           -> Json.arr(resourceCtxUri.asJson, iamCtxUri.asJson),
          "@id"                -> id.asJson,
          "@type"              -> Json.arr(types.map(t => Json.fromString(t.lastSegment.getOrElse(t.asString))).toSeq: _*),
          nxv.rev.prefix       -> Json.fromLong(rev),
          nxv.createdBy.prefix -> createdBy.id.asJson,
          nxv.updatedBy.prefix -> updatedBy.id.asJson,
          nxv.createdAt.prefix -> Json.fromString(createdAt.toString),
          nxv.updatedAt.prefix -> Json.fromString(updatedAt.toString)
        )
    }
}
