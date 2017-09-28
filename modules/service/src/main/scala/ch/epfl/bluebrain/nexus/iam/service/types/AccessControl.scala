package ch.epfl.bluebrain.nexus.iam.service.types

import ch.epfl.bluebrain.nexus.iam.core.acls.Permissions
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejections._
import io.circe.Decoder

final case class AccessControl(identity: Identity, permissions: Permissions)

object AccessControl {
  implicit val decoder: Decoder[AccessControl] = Decoder.instance { cursor =>
    val fields = cursor.fields.toSeq.flatten
    if (!fields.contains("permissions"))
      throw WrongOrInvalidJson(Some("Missing field 'permissions' in payload"))
    else if (!fields.contains("identity"))
      throw WrongOrInvalidJson(Some("Missing field 'identity' in payload"))
    else
      cursor.downField("permissions").as[Permissions] match {
        case Left(df) => throw IllegalPermissionString(df.message)
        case Right(permissions) =>
          cursor.downField("identity").as[Identity] match {
            case Left(df)        => throw IllegalIdentityFormat(df.message, "identity")
            case Right(identity) => Right(AccessControl(identity, permissions))
          }
      }
  }
}
