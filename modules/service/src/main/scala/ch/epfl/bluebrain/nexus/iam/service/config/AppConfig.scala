package ch.epfl.bluebrain.nexus.iam.service.config

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig._

import scala.concurrent.duration.Duration

/**
  * Case class which aggregates the configuration parameters
  *
  * @param description The service description namespace
  * @param instance Service instance specific settings
  * @param http Http binding settings
  * @param runtime Service runtime settings
  * @param cluster Cluster specific settings
  * @param persistence Persistence settings
  */
final case class AppConfig(description: DescriptionConfig,
                           instance: InstanceConfig,
                           http: HttpConfig,
                           runtime: RuntimeConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           auth: AuthConfig)

object AppConfig {

  final case class DescriptionConfig(name: String, environment: String) {
    val version: String = BuildInfo.version
    val ActorSystemName = s"$name-${version.replaceAll("\\.", "-")}-$environment"
  }

  final case class InstanceConfig(interface: String)

  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

  final case class RuntimeConfig(defaultTimeout: Duration)

  final case class ClusterConfig(passivationTimeout: Duration, shards: Int, seeds: Option[String]) {
    lazy val seedAddresses: Set[String] =
      seeds.map(_.split(",").toSet).getOrElse(Set.empty[String])
  }

  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  final case class AuthConfig(adminGroups: Set[String])

}
