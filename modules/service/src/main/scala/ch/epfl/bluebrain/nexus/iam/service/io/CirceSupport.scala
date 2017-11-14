package ch.epfl.bluebrain.nexus.iam.service.io

import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Printer
import io.circe.generic.extras.Configuration

/**
  * Json specific akka http circe support.
  */
trait CirceSupport extends FailFastCirceSupport {
  implicit val config: Configuration = Configuration.default.withDiscriminator("@type")
  implicit val printer               = Printer.noSpaces.copy(dropNullKeys = true)

}
object CirceSupport extends CirceSupport
