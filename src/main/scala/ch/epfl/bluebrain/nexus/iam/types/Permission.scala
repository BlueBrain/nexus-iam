package ch.epfl.bluebrain.nexus.iam.types

import cats.Show
import io.circe._

/**
  * Wraps a permission string, e.g. ''own'', ''read'', ''write''.
  *
  * @param value a valid permission string
  */
final case class Permission private (value: String)

object Permission {
  private val valid = "[a-zA-Z-_\\/]{1,16}".r

  final def apply(value: String): Option[Permission] =
    valid.findFirstIn(value).map(new Permission(_))

  /**
    * Resource ownership access permission definition. Owning a resource offers the ability
    * to change the ownership group and set permissions on all resources and sub-resources.
    */
  val Own = new Permission("own")

  /**
    * Resource read access permission definition. Read access to a resource allows
    * viewing the current state and history of the resource.
    */
  val Read = new Permission("read")

  /**
    * Resource write access permission definition. Write access to a resource allows
    * changing the current state of the resource.
    */
  val Write = new Permission("write")

  implicit val permShow: Show[Permission] = Show.show { case Permission(value) => value }

  implicit val permKeyEncoder: KeyEncoder[Permission] = KeyEncoder.encodeKeyString.contramap(_.value)

  implicit val permKeyDecoder: KeyDecoder[Permission] = KeyDecoder.instance(apply)

  implicit val permEncoder: Encoder[Permission] = Encoder.encodeString.contramap[Permission](_.value)

  implicit val permDecoder: Decoder[Permission] =
    Decoder.decodeString.emap(apply(_).toRight("Illegal permission format"))
}
