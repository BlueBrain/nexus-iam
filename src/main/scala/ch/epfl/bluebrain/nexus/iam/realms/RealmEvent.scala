package ch.epfl.bluebrain.nexus.iam.realms

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.types.{GrantType, Identity, Label}
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import io.circe.Json

/**
  * Enumeration of Realm event types.
  */
sealed trait RealmEvent extends Product with Serializable {

  /**
    * @return the label of the realm for which this event was emitted
    */
  def id: Label

  /**
    * @return the revision this event generated
    */
  def rev: Long

  /**
    * @return the instant when the event was emitted
    */
  def instant: Instant

  /**
    * @return the subject that performed the action that resulted in emitting this event
    */
  def subject: Subject
}

object RealmEvent {

  /**
    * A witness to a realm creation.
    *
    * @param id           the label of the realm
    * @param rev          the revision this event generated
    * @param name         the name of the realm
    * @param openIdConfig the address of the openid configuration
    * @param issuer       the issuer identifier
    * @param keys         the collection of keys
    * @param grantTypes   the types of OAuth2 grants supported
    * @param logo         an optional address for a logo
    * @param instant      the instant when the event was emitted
    * @param subject      the subject that performed the action that resulted in emitting this event
    */
  final case class RealmCreated(
      id: Label,
      rev: Long,
      name: String,
      openIdConfig: Url,
      issuer: String,
      keys: Set[Json],
      grantTypes: Set[GrantType],
      logo: Option[Url],
      instant: Instant,
      subject: Subject
  ) extends RealmEvent

  /**
    * A witness to a realm update.
    *
    * @param id           the label of the realm
    * @param rev          the revision this event generated
    * @param name         the name of the realm
    * @param openIdConfig the address of the openid configuration
    * @param issuer       the issuer identifier
    * @param keys         the collection of keys
    * @param grantTypes   the types of OAuth2 grants supported
    * @param logo         an optional address for a logo
    * @param instant      the instant when the event was emitted
    * @param subject      the subject that performed the action that resulted in emitting this event
    */
  final case class RealmUpdated(
      id: Label,
      rev: Long,
      name: String,
      openIdConfig: Url,
      issuer: String,
      keys: Set[Json],
      grantTypes: Set[GrantType],
      logo: Option[Url],
      instant: Instant,
      subject: Subject
  ) extends RealmEvent

  /**
    * A witness to a realm deprecation.
    *
    * @param id      the label of the realm
    * @param rev     the revision this event generated
    * @param instant the instant when the event was emitted
    * @param subject the subject that performed the action that resulted in emitting this event
    */
  final case class RealmDeprecated(
      id: Label,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends RealmEvent

  object JsonLd {
    import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
    import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
    import ch.epfl.bluebrain.nexus.iam.config.Contexts.{iamCtxUri, resourceCtxUri}
    import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
    import ch.epfl.bluebrain.nexus.iam.types.GrantType.Camel._
    import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._
    import io.circe.{Encoder, Json}

    private implicit val config: Configuration = Configuration.default
      .withDiscriminator("@type")
      .copy(transformMemberNames = {
        case "rev"        => "_rev"
        case "instant"    => "_instant"
        case "subject"    => "_subject"
        case "issuer"     => "_issuer"
        case "keys"       => "_keys"
        case "grantTypes" => "_grantTypes"
        case other        => other
      })

    implicit def realmEventEncoder(implicit http: HttpConfig): Encoder[Event] = {
      Encoder.encodeJson.contramap[Event] { ev =>
        implicit val subjectEncoder: Encoder[Subject] = Identity.subjectIdEncoder
        deriveEncoder[Event]
          .mapJson { json =>
            val id = Json.obj("@id" -> Json.fromString((http.realmsIri + ev.id.value).asUri))
            json
              .removeKeys("id")
              .deepMerge(id)
              .addContext(iamCtxUri)
              .addContext(resourceCtxUri)
          }
          .apply(ev)
      }
    }
  }
}
