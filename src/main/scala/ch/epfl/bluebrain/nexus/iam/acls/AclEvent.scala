package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.Path

/**
  * Enumeration of ACL event types.
  */
sealed trait AclEvent extends Product with Serializable {

  /**
    * @return the target path for the ACL
    */
  def path: Path

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

object AclEvent {

  /**
    * A witness to ACL replace.
    *
    * @param path    the target path for the ACL
    * @param acl     the ACL replaced, represented as a mapping of identities to permissions
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclReplaced(path: Path, acl: AccessControlList, rev: Long, instant: Instant, subject: Subject)
      extends AclEvent

  /**
    * A witness to ACL append.
    *
    * @param path    the target path for the ACL
    * @param acl     the ACL appended, represented as a mapping of identities to permissions
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclAppended(path: Path, acl: AccessControlList, rev: Long, instant: Instant, subject: Subject)
      extends AclEvent

  /**
    * A witness to ACL subtraction.
    *
    * @param path    the target path for the ACL
    * @param acl     the ACL subtracted, represented as a mapping of identities to permissions
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclSubtracted(path: Path, acl: AccessControlList, rev: Long, instant: Instant, subject: Subject)
      extends AclEvent

  /**
    * A witness to ACL deletion.
    *
    * @param path    the target path for the ACL
    * @param rev     the revision that this event generated
    * @param instant the instant when this event was recorded
    * @param subject the subject which generated this event
    */
  final case class AclDeleted(path: Path, rev: Long, instant: Instant, subject: Subject) extends AclEvent

  object JsonLd {
    import ch.epfl.bluebrain.nexus.iam.config.Contexts.{iamCtxUri, resourceCtxUri}
    import ch.epfl.bluebrain.nexus.rdf.instances._
    import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
    import io.circe.Encoder
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._
    import io.circe.java8.time._

    private implicit val config: Configuration = Configuration.default.withDiscriminator("@type")

    implicit def aclEventEncoder(implicit httpConfig: HttpConfig): Encoder[AclEvent] = {
      implicit val arrayEncoder: Encoder[AccessControlList] = AccessControlList.aclArrayEncoder
      implicit val subjectEncoder: Encoder[Subject]         = Identity.subjectIdEncoder
      deriveEncoder[AclEvent]
        .mapJson { json =>
          json
            .addContext(iamCtxUri)
            .addContext(resourceCtxUri)
        }
    }
  }
}
