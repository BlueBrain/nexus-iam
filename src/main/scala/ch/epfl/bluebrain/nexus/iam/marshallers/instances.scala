package ch.epfl.bluebrain.nexus.iam.marshallers

import akka.http.scaladsl.marshalling.GenericMarshallers.eitherMarshaller
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes._
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsRejection
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsRejection._
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection
import ch.epfl.bluebrain.nexus.iam.types.IamError.InternalError
import ch.epfl.bluebrain.nexus.iam.types.{IamError, ResourceRejection}
import ch.epfl.bluebrain.nexus.rdf.Iri.{AbsoluteIri, Url}
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.service.http.directives.StatusFrom
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.syntax._

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration

object instances extends FailFastCirceSupport {

  implicit val urlEncoder: Encoder[Url] = Encoder[AbsoluteIri].contramap(identity)
  implicit val urlDecoder: Decoder[Url] = Decoder.decodeString.emap(Url.apply)
  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.encodeString.contramap(fd => s"${fd.toMillis} ms")

  implicit val resourceRejectionEncoder: Encoder[ResourceRejection] =
    Encoder.instance {
      case r: AclRejection         => Encoder[AclRejection].apply(r)
      case r: RealmRejection       => Encoder[RealmRejection].apply(r)
      case r: PermissionsRejection => Encoder[PermissionsRejection].apply(r)
      case _                       => Encoder[IamError].apply(InternalError("unspecified"))
    }

  override def unmarshallerContentTypes: Seq[ContentTypeRange] =
    List(`application/json`, `application/ld+json`, `application/sparql-results+json`)

  /**
    * `Json` => HTTP entity
    *
    * @return marshaller for JSON-LD value
    */
  final implicit def jsonLd(
      implicit printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
      keys: OrderedKeys = orderedKeys
  ): ToEntityMarshaller[Json] = {
    val marshallers = Seq(`application/ld+json`, `application/json`).map(contentType =>
      Marshaller.withFixedContentType[Json, MessageEntity](contentType) { json =>
        HttpEntity(`application/ld+json`, printer.pretty(json.sortKeys))
    })
    Marshaller.oneOf(marshallers: _*)
  }

  /**
    * `A` => HTTP entity
    *
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  final implicit def httpEntity[A](
      implicit encoder: Encoder[A],
      printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
      keys: OrderedKeys = orderedKeys
  ): ToEntityMarshaller[A] =
    jsonLd.compose(encoder.apply)

  /**
    * `Either[Rejection,A]` => HTTP entity
    *
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit final def either[A: Encoder, B <: ResourceRejection: StatusFrom: Encoder](
      implicit printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  ): ToResponseMarshaller[Either[B, A]] =
    eitherMarshaller(rejection[B], httpEntity[A])

  /**
    * `Rejection` => HTTP response
    *
    * @return marshaller for Rejection value
    */
  implicit final def rejection[A <: ResourceRejection: Encoder](
      implicit statusFrom: StatusFrom[A],
      printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
      ordered: OrderedKeys = orderedKeys,
  ): ToResponseMarshaller[A] = {
    val marshallers = Seq(`application/ld+json`, `application/json`).map { contentType =>
      Marshaller.withFixedContentType[A, HttpResponse](contentType) { rejection =>
        HttpResponse(status = statusFrom(rejection),
                     entity = HttpEntity(contentType, printer.pretty(rejection.asJson.sortKeys)))
      }
    }
    Marshaller.oneOf(marshallers: _*)
  }
}
