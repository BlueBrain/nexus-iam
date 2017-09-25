package ch.epfl.bluebrain.nexus.iam.service.io

import java.nio.charset.Charset

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.extras.Configuration

/**
  * Provides Encoding from A to String, Byte[Array] and ByteString and also a type discriminator
  */
trait CirceSerialization extends FailFastCirceSupport {

  final implicit val configuration: Configuration = Configuration.default.withSnakeCaseKeys.withDiscriminator("type")

}

object CirceSerialization extends CirceSerialization {

  /**
    * Constant reference to the ''UTF-8'' charset
    */
  final val UTF8: Charset = Charset.forName("UTF-8")
}
