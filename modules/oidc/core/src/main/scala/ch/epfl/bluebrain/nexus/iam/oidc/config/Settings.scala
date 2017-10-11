package ch.epfl.bluebrain.nexus.iam.oidc.config

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.oidc.config.AppConfig._
import com.typesafe.config.Config
import pureconfig.ConvertHelpers.catchReadError
import pureconfig.{ConfigConvert, loadConfigOrThrow}

/**
  * Akka settings extension to expose application configuration.  It typically uses the configuration instance of the
  * actor system as the configuration root.
  *
  * @param config the configuration instance to read
  */
// $COVERAGE-OFF$
@SuppressWarnings(Array("LooksLikeInterpolatedString"))
class Settings(config: Config) extends Extension {

  private implicit val uriConverter: ConfigConvert[Uri] =
    ConfigConvert.viaString[Uri](catchReadError(s => Uri(s)), _.toString)

  val appConfig = AppConfig(
    loadConfigOrThrow[DescriptionConfig](config, "app.description"),
    loadConfigOrThrow[InstanceConfig](config, "app.instance"),
    loadConfigOrThrow[HttpConfig](config, "app.http"),
    loadConfigOrThrow[RuntimeConfig](config, "app.runtime"),
    loadConfigOrThrow[ClusterConfig](config, "app.cluster"),
    loadConfigOrThrow[OidcConfig](config, "app.oidc")
  )
}

object Settings extends ExtensionId[Settings] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension]                  = Settings
  override def createExtension(system: ExtendedActorSystem): Settings = new Settings(system.settings.config)
  def apply(config: Config): Settings                                 = new Settings(config)
}
// $COVERAGE-ON$
