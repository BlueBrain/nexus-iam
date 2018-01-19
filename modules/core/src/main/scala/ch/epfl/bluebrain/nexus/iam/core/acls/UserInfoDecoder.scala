package ch.epfl.bluebrain.nexus.iam.core.acls

import java.util.regex.Pattern

import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveDecoder

object UserInfoDecoder {

  object bbp {
    implicit def userInfoDecoder: Decoder[UserInfo] = {
      implicit val config = Configuration.default.withSnakeCaseMemberNames
      Decoder.decodeJson.emap { json =>
        deriveDecoder[UserInfo]
          .decodeJson(json)
          .fold(fa => Left(fa.message), ui => Right(unslashed(ui)))
      }
    }
  }

  object hbp {
    implicit val userInfoDecoder: Decoder[UserInfo] = {
      def toSet(value: String): Set[String] =
        value.split(",").filterNot(_.trim.isEmpty).toSet

      Decoder.forProduct7[String, String, String, String, String, String, String, UserInfo]("sub",
                                                                                            "name",
                                                                                            "preferred_username",
                                                                                            "given_name",
                                                                                            "family_name",
                                                                                            "email",
                                                                                            "groups") {
        case (sub, name, preferredUsername, givenName, familyName, email, groups) =>
          unslashed(UserInfo(sub, name, preferredUsername, givenName, familyName, email, toSet(groups)))
      }
    }
  }

  /**
    * Remove slashes from ''UserInfo'' groups and sub fields
    */
  private def unslashed(user: UserInfo): UserInfo =
    user.copy(sub = unslashed(user.sub), groups = user.groups.map(unslashed(_)))

  private def unslashed(value: String): String = value.replaceAll(Pattern.quote("/"), "")

}
