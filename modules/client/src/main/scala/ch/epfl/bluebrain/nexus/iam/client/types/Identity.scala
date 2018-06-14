package ch.epfl.bluebrain.nexus.iam.client.types

import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable

/**
  * Represents identities that were authenticated from a third party origin.
  */
sealed trait Authenticated

object Identity {

  /**
    * A user identity
    *
    * @param realm the authentication's realm name
    * @param sub   the subject name
    */
  final case class UserRef(realm: String, sub: String) extends Identity with Authenticated

  /**
    * A group identity
    *
    * @param realm the authentication's realm name
    * @param group the group name
    */
  final case class GroupRef(realm: String, group: String) extends Identity with Authenticated

  /**
    * An authenticated identity
    *
    * @param realm the optionally available authentication's realm name
    */
  final case class AuthenticatedRef(realm: Option[String]) extends Identity with Authenticated

  /**
    * An anonymous identity
    */
  final case object Anonymous extends Identity

  final implicit val clientConfig: Configuration              = Configuration.default.withDiscriminator("@type")
  final implicit val clientIdentityDecoder: Decoder[Identity] = deriveDecoder[Identity]
  final implicit val clientIdentityEncoder: Encoder[Identity] = deriveEncoder[Identity]
}
