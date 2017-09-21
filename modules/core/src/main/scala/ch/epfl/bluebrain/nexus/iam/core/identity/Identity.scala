package ch.epfl.bluebrain.nexus.iam.core.identity

import akka.http.scaladsl.model.Uri

import cats.Show
import io.circe._

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable

/**
  * Represents identities that were authenticated from a third party origin.
  */
trait Authenticated {

  /**
    * @return the third party domain from where the authentication origins
    */
  def origin: Uri
}

object Identity {

  /**
    * The ''user'' identity class.
    *
    * @param origin the authentication's origin
    * @param subject the JWT ''sub'' field
    */
  final case class UserRef(origin: Uri, subject: String) extends Identity with Authenticated

  /**
    * The ''group'' identity class.
    *
    * @param origin the authentication's origin
    * @param group the group name
    */
  final case class GroupRef(origin: Uri, group: String) extends Identity with Authenticated

  /**
    * The ''authenticated'' identity class that represents anyone authenticated from ''origin''.
    *
    * @param origin
    */
  final case class AuthenticatedRef(origin: Uri) extends Identity with Authenticated

  /**
    * The ''anonymous'' identity singleton that covers unknown and unauthenticated users.
    */
  final case object Anonymous extends Identity

  implicit val identityShow: Show[Identity] = Show.fromToString[Identity]

  implicit val identityDecoder: Decoder[Identity] =
    Decoder.forProduct3[Option[String], Option[String], Option[String], Identity]("origin", "subject", "group") {
      case (None, _, _)                      => Anonymous
      case (Some(origin), None, None)        => AuthenticatedRef(Uri(origin))
      case (Some(origin), None, Some(group)) => GroupRef(Uri(origin), group)
      case (Some(origin), Some(subject), _)  => UserRef(Uri(origin), subject)
    }

  implicit val identityEncoder: Encoder[Identity] = Encoder.encodeJson.contramap[Identity] {
    case Anonymous                => Json.Null
    case AuthenticatedRef(origin) => Json.obj("origin" -> Json.fromString(origin.toString))
    case GroupRef(origin, group) =>
      Json.obj("origin" -> Json.fromString(origin.toString), "group" -> Json.fromString(group))
    case UserRef(origin, subject) =>
      Json.obj("origin" -> Json.fromString(origin.toString), "subject" -> Json.fromString(subject))
  }

}
