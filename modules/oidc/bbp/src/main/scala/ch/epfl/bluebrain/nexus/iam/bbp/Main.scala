package ch.epfl.bluebrain.nexus.iam.bbp

import ch.epfl.bluebrain.nexus.iam.oidc.BootstrapService
import kamon.Kamon
import kamon.system.SystemMetrics

/**
  * Service entry point.
  */
// $COVERAGE-OFF$
object Main {

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.bbp._
    val _ = BootstrapService(startMonitoring, stopMonitoring)
  }

  private def startMonitoring = () => {
    SystemMetrics.startCollecting()
    Kamon.loadReportersFromConfig()
  }

  private def stopMonitoring = () => {
    Kamon.stopAllReporters()
    SystemMetrics.stopCollecting()
  }

}
// $COVERAGE-ON$
