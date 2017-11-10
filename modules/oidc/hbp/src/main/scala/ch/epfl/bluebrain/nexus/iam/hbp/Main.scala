package ch.epfl.bluebrain.nexus.iam.hbp

import ch.epfl.bluebrain.nexus.iam.oidc.BootstrapService
import kamon.Kamon

/**
  * Service entry point.
  */
// $COVERAGE-OFF$
object Main {

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.hbp._
    val _ = BootstrapService(() => Kamon.start(), () => Kamon.shutdown())
  }
}
// $COVERAGE-ON$
