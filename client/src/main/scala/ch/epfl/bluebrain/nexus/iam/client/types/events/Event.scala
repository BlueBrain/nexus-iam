package ch.epfl.bluebrain.nexus.iam.client.types.events

import java.time.Instant

import cats.implicits._
import ch.epfl.bluebrain.nexus.iam.client.types.AccessControlList.aclEntityDecoder
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.client.types.{AccessControlList, GrantType, Identity, Permission}
import ch.epfl.bluebrain.nexus.rdf.implicits._
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.{Path, Url}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredDecoder
import io.circe.{Decoder, DecodingFailure, Json}

/**
  * Enumeration of ACL event types.
  */
sealed trait Event extends Product with Serializable {

  /**
    * @return the revision that this event generated
    */
  def rev: Long

  /**
    * @return the instant when this event was created
    */
  def instant: Instant

  /**
    * @return the subject which created this event
    */
  def subject: Subject

}

object Event {

  sealed trait AclEvent extends Event {

    /**
      * @return the target path for the ACL
      */
    def path: Path
  }

  sealed trait PermissionsEvent extends Event

  sealed trait RealmEvent extends Event {

    /**
      * @return the label of the realm for which this event was emitted
      */
    def label: String
  }

  /**
    * A witness to ACL replace.
    *
    * @param path    the target path for the ACL
    * @param acl     the ACL replaced, represented as a mapping of identities to permissions
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclReplaced(
      path: Path,
      acl: AccessControlList,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends AclEvent

  /**
    * A witness to ACL append.
    *
    * @param path    the target path for the ACL
    * @param acl     the ACL appended, represented as a mapping of identities to permissions
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclAppended(
      path: Path,
      acl: AccessControlList,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends AclEvent

  /**
    * A witness to ACL subtraction.
    *
    * @param path    the target path for the ACL
    * @param acl     the ACL subtracted, represented as a mapping of identities to permissions
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclSubtracted(
      path: Path,
      acl: AccessControlList,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends AclEvent

  /**
    * A witness to ACL deletion.
    *
    * @param path    the target path for the ACL
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclDeleted(
      path: Path,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends AclEvent

  /**
    * A witness to a collection of permissions appended to the set.
    *
    * @param permissions the collection of permissions appended to the set
    * @param rev         the revision this event generated
    * @param instant     the instant when the event was emitted
    * @param subject     the subject that performed the action that resulted in emitting this event
    */
  final case class PermissionsAppended(
      permissions: Set[Permission],
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends PermissionsEvent

  /**
    * A witness to a collection of permissions subtracted from the set.
    *
    * @param permissions the collection of permissions subtracted from the set
    * @param rev         the revision this event generated
    * @param instant     the instant when the event was emitted
    * @param subject     the subject that performed the action that resulted in emitting this event
    */
  final case class PermissionsSubtracted(
      permissions: Set[Permission],
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends PermissionsEvent

  /**
    * A witness to the permission set being replaced.
    *
    * @param permissions the new set of permissions that replaced the previous set
    * @param rev         the revision this event generated
    * @param instant     the instant when the event was emitted
    * @param subject     the subject that performed the action that resulted in emitting this event
    */
  final case class PermissionsReplaced(
      permissions: Set[Permission],
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends PermissionsEvent

  /**
    * A witness to the permission set being deleted (emptied).
    *
    * @param rev     the revision this event generated
    * @param instant the instant when the event was emitted
    * @param subject the subject that performed the action that resulted in emitting this event
    */
  final case class PermissionsDeleted(
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends PermissionsEvent

  /**
    * A witness to a realm creation.
    *
    * @param label                 the label of the realm
    * @param rev                   the revision this event generated
    * @param name                  the name of the realm
    * @param openIdConfig          the address of the openid configuration
    * @param issuer                the issuer identifier
    * @param keys                  the collection of keys
    * @param grantTypes            the types of OAuth2 grants supported
    * @param logo                  an optional address for a logo
    * @param authorizationEndpoint the authorization endpoint
    * @param tokenEndpoint         the token endpoint
    * @param userInfoEndpoint      the user info endpoint
    * @param revocationEndpoint    an optional revocation endpoint
    * @param endSessionEndpoint    an optional end session endpoint
    * @param instant               the instant when the event was emitted
    * @param subject               the subject that performed the action that resulted in emitting this event
    */
  final case class RealmCreated(
      label: String,
      rev: Long,
      name: String,
      openIdConfig: Url,
      issuer: String,
      keys: Set[Json],
      grantTypes: Set[GrantType],
      logo: Option[Url],
      authorizationEndpoint: Url,
      tokenEndpoint: Url,
      userInfoEndpoint: Url,
      revocationEndpoint: Option[Url],
      endSessionEndpoint: Option[Url],
      instant: Instant,
      subject: Subject
  ) extends RealmEvent

  /**
    * A witness to a realm update.
    *
    * @param label                 the label of the realm
    * @param rev                   the revision this event generated
    * @param name                  the name of the realm
    * @param openIdConfig          the address of the openid configuration
    * @param issuer                the issuer identifier
    * @param keys                  the collection of keys
    * @param grantTypes            the types of OAuth2 grants supported
    * @param logo                  an optional address for a logo
    * @param authorizationEndpoint the authorization endpoint
    * @param tokenEndpoint         the token endpoint
    * @param userInfoEndpoint      the user info endpoint
    * @param revocationEndpoint    an optional revocation endpoint
    * @param endSessionEndpoint    an optional end session endpoint
    * @param instant               the instant when the event was emitted
    * @param subject               the subject that performed the action that resulted in emitting this event
    */
  final case class RealmUpdated(
      label: String,
      rev: Long,
      name: String,
      openIdConfig: Url,
      issuer: String,
      keys: Set[Json],
      grantTypes: Set[GrantType],
      logo: Option[Url],
      authorizationEndpoint: Url,
      tokenEndpoint: Url,
      userInfoEndpoint: Url,
      revocationEndpoint: Option[Url],
      endSessionEndpoint: Option[Url],
      instant: Instant,
      subject: Subject
  ) extends RealmEvent

  /**
    * A witness to a realm deprecation.
    *
    * @param label   the label of the realm
    * @param rev     the revision this event generated
    * @param instant the instant when the event was emitted
    * @param subject the subject that performed the action that resulted in emitting this event
    */
  final case class RealmDeprecated(
      label: String,
      rev: Long,
      instant: Instant,
      subject: Subject
  ) extends RealmEvent

  private[events] implicit val config: Configuration = Configuration.default
    .withDiscriminator("@type")
    .copy(transformMemberNames = {
      case "label"                 => "_label"
      case "rev"                   => "_rev"
      case "instant"               => "_instant"
      case "subject"               => "_subject"
      case "path"                  => "_path"
      case "issuer"                => "_issuer"
      case "keys"                  => "_keys"
      case "grantTypes"            => "_grantTypes"
      case "authorizationEndpoint" => "_authorizationEndpoint"
      case "tokenEndpoint"         => "_tokenEndpoint"
      case "userInfoEndpoint"      => "_userInfoEndpoint"
      case "revocationEndpoint"    => "_revocationEndpoint"
      case "endSessionEndpoint"    => "_endSessionEndpoint"
      case other                   => other
    })

  private[events] implicit val subjectDecoder: Decoder[Subject] =
    Decoder.decodeString.flatMap { id =>
      Iri.absolute(id) match {
        case Left(_) => Decoder.failedWithMessage(s"Couldn't convert id '$id' to Absolute Iri")
        case Right(iri) =>
          Identity(iri) match {
            case Some(s: Subject) => Decoder.const(s)
            case _                => Decoder.failedWithMessage(s"Couldn't decode subject from '$id'")
          }
      }
    }

  /**
    * [[Decoder]] for [[Event]]s.
    */
  implicit val eventDecoder: Decoder[Event] =
    deriveConfiguredDecoder[Event]

  private[events] implicit val aclDecoder: Decoder[AccessControlList] =
    Decoder.instance { hc =>
      for {
        arr <- hc.focus.flatMap(_.asArray).toRight(DecodingFailure("acl field not found", hc.history))
        acl <- arr.foldM(Map.empty[Identity, Set[Permission]]) {
          case (acc, j) => aclEntityDecoder(j.hcursor).map(acc + _)
        }
      } yield AccessControlList(acl)
    }
}
