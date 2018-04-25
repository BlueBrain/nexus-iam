package ch.epfl.bluebrain.nexus.iam.service.config

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.iam.elastic.ElasticConfig
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig._

import scala.concurrent.duration.{Duration, FiniteDuration}

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
                           auth: AuthConfig,
                           oidc: OidcConfig,
                           context: ContextConfig,
                           kafka: KafkaConfig,
                           elastic: ElasticConfig)

object AppConfig {

  final case class DescriptionConfig(name: String, environment: String) {
    val version: String = BuildInfo.version
    val actorSystemName = s"$name-${version.replaceAll("\\W", "-")}-$environment"
  }

  final case class InstanceConfig(interface: String)

  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

  final case class RuntimeConfig(defaultTimeout: FiniteDuration)

  final case class ClusterConfig(passivationTimeout: Duration, shards: Int, seeds: Option[String]) {
    lazy val seedAddresses: Set[String] =
      seeds.map(_.split(",").toSet).getOrElse(Set.empty[String])
  }

  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  final case class AuthConfig(testMode: Boolean, adminGroups: Set[String])

  final case class OidcConfig(providers: List[OidcProviderConfig], defaultRealm: String)

  final case class OidcProviderConfig(realm: String,
                                      issuer: Uri,
                                      jwkCert: Uri,
                                      authorizeEndpoint: Uri,
                                      tokenEndpoint: Uri,
                                      userinfoEndpoint: Uri)

  final case class ContextConfig(error: ContextUri, iam: ContextUri)

  final case class KafkaConfig(permissionsTopic: String, permissionsProjectionId: String)

}
