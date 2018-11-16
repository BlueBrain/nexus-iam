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
  private val valid = "[a-zA-Z-:_\\/]{1,32}".r

  final def apply(value: String): Option[Permission] =
    valid.findFirstIn(value).map(unsafe)

  final def unsafe(value: String): Permission =
    new Permission(value)

  /**
    * Resource ownership access permission definition. Owning a resource offers the ability
    * to change the ownership group and set permissions on all resources and sub-resources.
    */
  val Own = new Permission("own")

  implicit val permShow: Show[Permission] = Show.show { case Permission(value) => value }

  implicit val permKeyEncoder: KeyEncoder[Permission] = KeyEncoder.encodeKeyString.contramap(_.value)

  implicit val permKeyDecoder: KeyDecoder[Permission] = KeyDecoder.instance(apply)

  implicit val permEncoder: Encoder[Permission] = Encoder.encodeString.contramap[Permission](_.value)

  implicit val permDecoder: Decoder[Permission] =
    Decoder.decodeString.emap(apply(_).toRight("Illegal permission format"))
}
