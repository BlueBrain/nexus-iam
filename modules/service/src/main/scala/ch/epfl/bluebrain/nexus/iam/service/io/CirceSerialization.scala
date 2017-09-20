package ch.epfl.bluebrain.nexus.iam.service.io

import java.nio.charset.Charset

import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.syntax._

/**
  * Provides Encoding from A to String, Byte[Array] and ByteString and also a type discriminator
  */
trait CirceSerialization extends FailFastCirceSupport {

  final implicit val configuration: Configuration = Configuration.default.withSnakeCaseKeys.withDiscriminator("type")

  /**
    * Conversion to String
    * @param a the instance of the object you want to convert to String
    * @tparam A the type parameter of the object you want to convert to String
    * @return the string representation of a
    */
  final def aToJsonString[A: Encoder](a: A): String = a.asJson.noSpaces

  /**
    * Conversion to Array[Byte]
    * @param a the instance of the object you want to convert to Array[Byte]
    * @tparam A the type parameter of the object you want to convert to Array[Byte]
    * @return the Array[Byte] representation of a
    */
  final def aToByteArray[A: Encoder](a: A): Array[Byte] = aToJsonString(a).getBytes(CirceSerialization.UTF8)

  /**
    * Conversion to ByteString
    * @param a the instance of the object you want to convert to ByteString
    * @tparam A the type parameter of the object you want to convert to ByteString
    * @return the ByteString representation of a
    */
  final def aToByteString[A: Encoder](a: A): ByteString = ByteString(aToJsonString(a))

}

object CirceSerialization extends CirceSerialization {

  /**
    * Constant reference to the ''UTF-8'' charset
    */
  final val UTF8: Charset = Charset.forName("UTF-8")
}
