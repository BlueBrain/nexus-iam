package ch.epfl.bluebrain.nexus.iam.service.queue

import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.{Encoder, Json, Printer}

trait KafkaMarshaller[A] {

  def apply(a: A): String

}

object KafkaMarshaller {

  implicit def jsonLdMarshaller[A](implicit apiUri: ApiUri,
                                   encoder: Encoder[A],
                                   printer: Printer = Printer.noSpaces.copy(dropNullKeys = true)): KafkaMarshaller[A] =
    (a: A) => {
      val context = Json.obj(
        "@context" -> Json.fromString(apiUri.base.copy(path = apiUri.base.path / "context").toString())
      )
      encoder.apply(a).deepMerge(context).pretty(printer)
    }

}
