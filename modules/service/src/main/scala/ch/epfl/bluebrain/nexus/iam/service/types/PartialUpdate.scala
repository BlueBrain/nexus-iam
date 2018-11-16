package ch.epfl.bluebrain.nexus.iam.service.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permissions
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveDecoder

/**
  * Base enumeration type for PartialUpdate message classes.
  */
sealed trait PartialUpdate extends Product with Serializable

/**
  * Match message which subtracts ''permissions'' for an ''identity''
  *
  * @param identity    the identity from where to subtract permissions
  * @param permissions the permissions to be subtracted
  */
final case class Subtract(identity: Identity, permissions: Permissions) extends PartialUpdate

object PartialUpdate {

  implicit def patchDecoder(implicit identity: Decoder[Identity]): Decoder[PartialUpdate] = {
    implicit val config: Configuration = Configuration.default.withDiscriminator("@type")
    deriveDecoder[PartialUpdate]
  }

}
