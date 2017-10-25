package ch.epfl.bluebrain.nexus.iam.service.queue

import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.{Encoder, Json, Printer}

/**
  * A generic marshaller that marshalls objects of type `A` to `String`
  * @tparam A type of object to be marshalled
  */
trait KafkaMarshaller[A] {

  def apply(a: A): String

}

object KafkaMarshaller {

  /**
    * Creates a marshaller that uses Circe Encoder[A] and adds @context field
    * @param apiUri object containing base URI for the API
    * @param encoder circe encoder for A
    * @param printer printer to be used when marshalling into `String`
    * @tparam A Class of object to be marshalled
    * @return `KafkaMarshaller` for type `A`
    */
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
