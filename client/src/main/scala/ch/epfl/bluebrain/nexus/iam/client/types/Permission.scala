package ch.epfl.bluebrain.nexus.iam.client.types

import cats.Show
import io.circe._

/**
  * Wraps a permission string.
  *
  * @param value a valid permission string
  */
final case class Permission private (value: String)

object Permission {
  private val valid = "[a-zA-Z-:_\\/]{1,32}".r

  /**
    * Attempts to construct a [[Permission]] that passes the ''regex''
    *
    * @param value the permission value
    */
  final def apply(value: String): Option[Permission] =
    valid.findFirstIn(value).map(unsafe)

  /**
    * Constructs a [[Permission]] without validating it against the ''regex''
    *
    * @param value the permission value
    */
  final def unsafe(value: String): Permission =
    new Permission(value)

  implicit val permShow: Show[Permission] = Show.show { case Permission(value) => value }

  implicit val permKeyEncoder: KeyEncoder[Permission] = KeyEncoder.encodeKeyString.contramap(_.value)

  implicit val permKeyDecoder: KeyDecoder[Permission] = KeyDecoder.instance(apply)

  implicit val permEncoder: Encoder[Permission] = Encoder.encodeString.contramap[Permission](_.value)

  implicit val permDecoder: Decoder[Permission] =
    Decoder.decodeString.emap(apply(_).toRight("Illegal permission format"))
}
