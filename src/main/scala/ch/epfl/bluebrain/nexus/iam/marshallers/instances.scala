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
import ch.epfl.bluebrain.nexus.iam.auth.TokenRejection
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.config.Contexts._
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsRejection
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsRejection._
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection.{IncorrectRev => _, _}
import ch.epfl.bluebrain.nexus.iam.routes.ResourceRejection
import ch.epfl.bluebrain.nexus.iam.routes.ResourceRejection.{IllegalParameter, Unexpected}
import ch.epfl.bluebrain.nexus.iam.types.IamError
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveEncoder
import io.circe.syntax._

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration

object instances extends FailFastCirceSupport {

  private implicit val rejectionConfig: Configuration = Configuration.default.withDiscriminator("@type")

  implicit val finiteDurationEncoder: Encoder[FiniteDuration] =
    Encoder.encodeString.contramap(fd => s"${fd.toMillis} ms")

  implicit val urlEncoder: Encoder[Url] =
    Encoder.encodeString.contramap(url => url.asUri)

  implicit val urlDecoder: Decoder[Url] =
    Decoder.decodeString.emap(Url.apply)

  implicit val iamErrorEncoder: Encoder[IamError] = {
    val enc = deriveEncoder[IamError].mapJson(_ addContext errorCtxUri)
    Encoder.instance(r => enc(r) deepMerge Json.obj("reason" -> Json.fromString(r.msg)))
  }

  implicit val aclRejectionEncoder: Encoder[AclRejection] = {
    val enc = deriveEncoder[AclRejection].mapJson(_ addContext errorCtxUri)
    Encoder.instance(r => enc(r) deepMerge Json.obj("reason" -> Json.fromString(r.msg)))
  }

  implicit val permissionRejectionEncoder: Encoder[PermissionsRejection] = {
    val enc = deriveEncoder[PermissionsRejection].mapJson(_ addContext errorCtxUri)
    Encoder.instance(r => enc(r) deepMerge Json.obj("reason" -> Json.fromString(r.msg)))
  }

  implicit val realmRejectionEncoder: Encoder[RealmRejection] = {
    val enc = deriveEncoder[RealmRejection].mapJson(_ addContext errorCtxUri)
    Encoder.instance(r => enc(r) deepMerge Json.obj("reason" -> Json.fromString(r.msg)))

  }

  implicit val tokenRejectionEncoder: Encoder[TokenRejection] = {
    val enc = deriveEncoder[TokenRejection]
    Encoder.instance(r => enc(r) deepMerge Json.obj("reason" -> Json.fromString(r.msg)))
  }

  implicit val resourceRejectionEncoder: Encoder[ResourceRejection] =
    deriveEncoder[ResourceRejection].mapJson(_ addContext errorCtxUri)

  implicit val httpRejectionEncoder: Encoder[HttpRejection] =
    deriveEncoder[HttpRejection].mapJson(_ addContext errorCtxUri)

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
        case _: NothingToBeUpdated                        => StatusCodes.BadRequest
        case _: AclIsEmpty                                => StatusCodes.BadRequest
        case _: AclCannotContainEmptyPermissionCollection => StatusCodes.BadRequest
        case _: AclNotFound                               => StatusCodes.NotFound
        case _: AclRejection.IncorrectRev                 => StatusCodes.Conflict
        case _: UnknownPermissions                        => StatusCodes.BadRequest
      }
    }

  implicit val permissionsStatusCode: RejectionStatusCode[PermissionsRejection] =
    new RejectionStatusCode[PermissionsRejection] {
      override def apply(rej: PermissionsRejection): StatusCode = rej match {
        case CannotSubtractEmptyCollection          => StatusCodes.BadRequest
        case _: CannotSubtractFromMinimumCollection => StatusCodes.BadRequest
        case CannotSubtractFromEmptyCollection      => StatusCodes.BadRequest
        case _: CannotSubtractUndefinedPermissions  => StatusCodes.BadRequest
        case CannotAppendEmptyCollection            => StatusCodes.BadRequest
        case CannotReplaceWithEmptyCollection       => StatusCodes.BadRequest
        case CannotDeleteMinimumCollection          => StatusCodes.BadRequest
        case _: PermissionsRejection.IncorrectRev   => StatusCodes.Conflict
      }
    }

  implicit val realmsStatusCode: RejectionStatusCode[RealmRejection] =
    new RejectionStatusCode[RealmRejection] {
      override def apply(rej: RealmRejection): StatusCode = rej match {
        case _: RealmAlreadyExists               => StatusCodes.BadRequest
        case _: RealmNotFound                    => StatusCodes.BadRequest
        case _: RealmAlreadyDeprecated           => StatusCodes.BadRequest
        case _: RealmRejection.IncorrectRev      => StatusCodes.Conflict
        case _: IllegalGrantTypeFormat           => StatusCodes.BadRequest
        case _: IllegalIssuerFormat              => StatusCodes.BadRequest
        case _: IllegalJwksUriFormat             => StatusCodes.BadRequest
        case _: IllegalJwkFormat                 => StatusCodes.BadRequest
        case _: UnsuccessfulJwksResponse         => StatusCodes.BadRequest
        case _: UnsuccessfulOpenIdConfigResponse => StatusCodes.BadRequest
        case _: NoValidKeysFound                 => StatusCodes.BadRequest
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
