package ch.epfl.bluebrain.nexus.iam.service.types

import ch.epfl.bluebrain.nexus.commons.iam.acls.Permissions
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, Encoder, Json}

/**
  * Base enumeration type for PartialUpdate message classes.
  */
sealed trait PartialUpdate extends Product with Serializable {

  /**
    * @return the context object of the partial update message
    */
  def `@context`: Option[Json]
}

/**
  * Match message which subtracts ''permissions'' for an ''identity''
  *
  * @param identity    the identity from where to subtract permissions
  * @param permissions the permissions to be subtracted
  * @param `@context`  the context object of the message
  */
final case class Subtract(identity: Identity, permissions: Permissions, `@context`: Option[Json] = None) extends PartialUpdate

object PartialUpdate {

  implicit def patchEncoder(implicit identity: Encoder[Identity]): Encoder[PartialUpdate] = {
    implicit val config: Configuration = Configuration.default.withDiscriminator("@type")
    io.circe.generic.extras.semiauto.deriveEncoder[PartialUpdate]
  }
  implicit def patchDecoder(implicit identity: Decoder[Identity]): Decoder[PartialUpdate] = {
    implicit val config: Configuration = Configuration.default.withDiscriminator("@type")
    io.circe.generic.extras.semiauto.deriveDecoder[PartialUpdate]
  }

}
