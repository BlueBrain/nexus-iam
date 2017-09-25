package ch.epfl.bluebrain.nexus.iam.service.types

import io.circe.{Decoder, Encoder}

/**
  * A boxed value representation that also provides a collection of related resource addresses.
  *
  * @param value the boxed value
  * @param links the collection of related resource addresses
  * @tparam A the type of boxed value
  */
final case class Boxed[A](value: A, links: List[Link])

object Boxed {
  implicit def boxedEncoder[A](implicit A: Encoder[A], L: Encoder[List[Link]]): Encoder[Boxed[A]] =
    Encoder.instance { boxed =>
      A(boxed.value).mapObject { jo =>
        jo.add("_links", L(boxed.links))
      }
    }

  implicit def boxedDecoder[A](implicit A: Decoder[A], L: Decoder[List[Link]]): Decoder[Boxed[A]] =
    Decoder.instance { cursor =>
      for {
        a <- cursor.as[A]
        l <- cursor.get[List[Link]]("_links")
      } yield Boxed(a, l)
    }
}
