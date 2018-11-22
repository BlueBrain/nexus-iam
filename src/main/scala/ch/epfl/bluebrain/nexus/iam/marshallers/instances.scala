package ch.epfl.bluebrain.nexus.iam.marshallers

import akka.http.scaladsl.marshalling.GenericMarshallers.eitherMarshaller
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes._
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.config.Contexts._
import ch.epfl.bluebrain.nexus.iam.routes.ResourceRejection
import ch.epfl.bluebrain.nexus.iam.routes.ResourceRejection.{IllegalParameter, Unexpected}
import ch.epfl.bluebrain.nexus.iam.types.IamError
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.syntax._

import scala.collection.immutable.Seq

object instances extends FailFastCirceSupport {

  private val rejectionConfig: Configuration = Configuration.default.withDiscriminator("code")

  implicit val iamErrorEncoder: Encoder[IamError] = {
    implicit val config = rejectionConfig
    deriveEncoder[IamError].mapJson(_ addContext errorCtxUri)
  }

  implicit val aclRejectionEncoder: Encoder[AclRejection] = {
    implicit val config = rejectionConfig
    deriveEncoder[AclRejection].mapJson(_ addContext errorCtxUri)
  }

  implicit val resourceRejectionEncoder: Encoder[ResourceRejection] = {
    implicit val config = rejectionConfig
    deriveEncoder[ResourceRejection].mapJson(_ addContext errorCtxUri)
  }

  implicit val httpRejectionEncoder: Encoder[HttpRejection] = {
    implicit val config = rejectionConfig
    deriveEncoder[HttpRejection].mapJson(_ addContext errorCtxUri)
  }

  override def unmarshallerContentTypes: Seq[ContentTypeRange] =
    List(`application/json`, `application/ld+json`, `application/sparql-results+json`)

  /**
    * `Json` => HTTP entity
    *
    * @return marshaller for JSON-LD value
    */
  final implicit def jsonLd(implicit printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                            keys: OrderedKeys = orderedKeys): ToEntityMarshaller[Json] = {
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
  final implicit def httpEntity[A](implicit encoder: Encoder[A],
                                   printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                                   keys: OrderedKeys = orderedKeys): ToEntityMarshaller[A] =
    jsonLd.compose(encoder.apply)

  /**
    * `Either[Rejection,A]` => HTTP entity
    *
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit final def either[A: Encoder, B: RejectionStatusCode: Encoder](
      implicit
      printer: Printer = Printer.noSpaces.copy(dropNullValues = true)): ToResponseMarshaller[Either[B, A]] =
    eitherMarshaller(rejection[B], httpEntity[A])

  /**
    * `Rejection` => HTTP response
    *
    * @return marshaller for Rejection value
    */
  implicit final def rejection[A: Encoder](implicit printer: Printer = Printer.noSpaces.copy(dropNullValues = true),
                                           ordered: OrderedKeys = orderedKeys,
                                           statusCodeFrom: RejectionStatusCode[A]): ToResponseMarshaller[A] = {
    val marshallers = Seq(`application/ld+json`, `application/json`).map { contentType =>
      Marshaller.withFixedContentType[A, HttpResponse](contentType) { rejection =>
        HttpResponse(status = statusCodeFrom(rejection),
                     entity = HttpEntity(contentType, printer.pretty(rejection.asJson.sortKeys)))
      }
    }
    Marshaller.oneOf(marshallers: _*)
  }

  sealed trait RejectionStatusCode[A] extends (A => StatusCode)

  implicit val aclStatusCode: RejectionStatusCode[AclRejection] =
    new RejectionStatusCode[AclRejection] {
      override def apply(rej: AclRejection): StatusCode = rej match {
        case _: NothingToBeUpdated         => StatusCodes.BadRequest
        case _: AclIsEmpty                 => StatusCodes.BadRequest
        case _: AclInvalidEmptyPermissions => StatusCodes.BadRequest
        case _: AclNotFound                => StatusCodes.NotFound
        case _: AclIncorrectRev            => StatusCodes.Conflict
        case AclMissingSubject             => StatusCodes.Unauthorized
      }
    }

  implicit val commonStatusCode: RejectionStatusCode[ResourceRejection] =
    new RejectionStatusCode[ResourceRejection] {
      override def apply(rej: ResourceRejection): StatusCode = rej match {
        case _: IllegalParameter => StatusCodes.BadRequest
        case _: Unexpected       => StatusCodes.InternalServerError
      }
    }
}
