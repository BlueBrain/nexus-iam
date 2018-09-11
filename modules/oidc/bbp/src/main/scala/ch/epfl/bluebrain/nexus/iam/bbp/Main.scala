package ch.epfl.bluebrain.nexus.iam.bbp

import ch.epfl.bluebrain.nexus.iam.oidc.{BootstrapService, ExternalConfig}
import com.typesafe.config.Config
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
    val config = ExternalConfig("IAM_BBP_CONFIG_FILE", "iam-bbp.config.file")
    val _      = BootstrapService(config, startMonitoring(config), stopMonitoring)
  }

  private def startMonitoring(config: Config) = () => {
    Kamon.reconfigure(config)
    SystemMetrics.startCollecting()
    Kamon.loadReportersFromConfig()
  }

  private def stopMonitoring = () => {
    Kamon.stopAllReporters()
    SystemMetrics.stopCollecting()
  }

}
// $COVERAGE-ON$
