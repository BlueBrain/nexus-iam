package ch.epfl.bluebrain.nexus.iam.core.identity

import akka.http.scaladsl.model.{IllegalUriException, Uri}
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

  implicit val identityDecoder: Decoder[Identity] = Decoder.instance { cursor =>
    try {
      for {
        o <- cursor.downField("origin").as[Option[String]]
        s <- cursor.downField("subject").as[Option[String]]
        g <- cursor.downField("group").as[Option[String]]
      } yield {
        o match {
          case None => Anonymous
          case Some(origin) =>
            val uri = Uri(origin)
            (s, g) match {
              case (None, None)        => AuthenticatedRef(uri)
              case (None, Some(group)) => GroupRef(uri, group)
              case (Some(subject), _)  => UserRef(uri, subject)
            }
        }
      }
    } catch {
      case e: IllegalUriException => Left(DecodingFailure(e.getMessage, cursor.history))
    }
  }

  implicit val identityEncoder: Encoder[Identity] = Encoder.encodeJson.contramap[Identity] {
    case Anonymous                => Json.obj()
    case AuthenticatedRef(origin) => Json.obj("origin" -> Json.fromString(origin.toString))
    case GroupRef(origin, group) =>
      Json.obj("origin" -> Json.fromString(origin.toString), "group" -> Json.fromString(group))
    case UserRef(origin, subject) =>
      Json.obj("origin" -> Json.fromString(origin.toString), "subject" -> Json.fromString(subject))
  }

}
