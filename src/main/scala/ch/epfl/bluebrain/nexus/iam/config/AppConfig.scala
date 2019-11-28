package ch.epfl.bluebrain.nexus.iam.config

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.cache.KeyValueStoreConfig
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.sourcing.akka.SourcingConfig
import ch.epfl.bluebrain.nexus.sourcing.akka.SourcingConfig.RetryStrategyConfig
import ch.epfl.bluebrain.nexus.sourcing.projections.IndexingConfig

import scala.concurrent.duration._

/**
  * Application configuration
  *
  * @param description service description
  * @param http        http interface configuration
  * @param cluster     akka cluster configuration
  * @param persistence persistence configuration
  * @param indexing    indexing configuration
  * @param acls        configuration for acls
  * @param permissions configuration for permissions
  * @param realms      configuration for realms
  * @param groups      configuration for groups
  */
final case class AppConfig(
    description: Description,
    http: HttpConfig,
    cluster: ClusterConfig,
    persistence: PersistenceConfig,
    indexing: IndexingConfig,
    acls: AclsConfig,
    permissions: PermissionsConfig,
    realms: RealmsConfig,
    groups: GroupsConfig
)

object AppConfig {

  /**
    * Service description
    *
    * @param name service name
    */
  final case class Description(name: String) {

    /**
      * @return the version of the service
      */
    val version: String = BuildInfo.version

    /**
      * @return the full name of the service (name + version)
      */
    val fullName: String = s"$name-${version.replaceAll("\\W", "-")}"

  }

  /**
    * HTTP configuration
    *
    * @param interface  interface to bind to
    * @param port       port to bind to
    * @param prefix     prefix to add to HTTP routes
    * @param publicUri  public URI of the service
    */
  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri) {
    lazy val publicIri: AbsoluteIri      = url"$publicUri".value
    lazy val prefixIri: AbsoluteIri      = url"$publicUri/$prefix".value
    lazy val aclsIri: AbsoluteIri        = url"$publicUri/$prefix/acls".value
    lazy val permissionsIri: AbsoluteIri = url"$publicUri/$prefix/permissions".value
    lazy val realmsIri: AbsoluteIri      = url"$publicUri/$prefix/realms".value
  }

  /**
    * Cluster configuration
    *
    * @param passivationTimeout actor passivation timeout
    * @param replicationTimeout replication / distributed data timeout
    * @param shards             number of shards in the cluster
    * @param seeds              seed nodes in the cluster
    */
  final case class ClusterConfig(
      passivationTimeout: FiniteDuration,
      replicationTimeout: FiniteDuration,
      shards: Int,
      seeds: Option[String]
  )

  /**
    * Persistence configuration
    *
    * @param journalPlugin        plugin for storing events
    * @param snapshotStorePlugin  plugin for storing snapshots
    * @param queryJournalPlugin   plugin for querying journal events
    */
  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  /**
    * ACLs configuration
    *
    * @param sourcing the acls sourcing configuration
    * @param indexing the indexing configuration
    */
  final case class AclsConfig(sourcing: SourcingConfig, indexing: IndexingConfig)

  /**
    * Permissions configuration.
    *
    * @param sourcing the permissions sourcing configuration
    * @param minimum  the minimum set of permissions
    */
  final case class PermissionsConfig(sourcing: SourcingConfig, minimum: Set[Permission])

  /**
    * Realms configuration.
    *
    * @param sourcing      the realms sourcing configuration
    * @param keyValueStore the key value store configuration
    * @param indexing      the indexing configuration
    */
  final case class RealmsConfig(sourcing: SourcingConfig, keyValueStore: KeyValueStoreConfig, indexing: IndexingConfig)

  /**
    * Group cache configuration.
    *
    * @param passivationTimeout actor passivation timeout
    * @param askTimeout         timeout for the message exchange with the actor
    * @param shards             the number of shards for the groups config
    * @param retry              the retry strategy for getting the user information
    */
  final case class GroupsConfig(
      passivationTimeout: FiniteDuration,
      askTimeout: FiniteDuration,
      shards: Int,
      retry: RetryStrategyConfig
  )

  val orderedKeys = OrderedKeys(
    List(
      "@context",
      "@id",
      "@type",
      nxv.reason.prefix,
      nxv.total.prefix,
      nxv.maxScore.prefix,
      nxv.results.prefix,
      nxv.score.prefix,
      "",
      nxv.label.prefix,
      nxv.path.prefix,
      nxv.grantTypes.prefix,
      nxv.issuer.prefix,
      nxv.keys.prefix,
      nxv.authorizationEndpoint.prefix,
      nxv.tokenEndpoint.prefix,
      nxv.userInfoEndpoint.prefix,
      nxv.revocationEndpoint.prefix,
      nxv.endSessionEndpoint.prefix,
      nxv.self.prefix,
      nxv.constrainedBy.prefix,
      nxv.project.prefix,
      nxv.rev.prefix,
      nxv.deprecated.prefix,
      nxv.createdAt.prefix,
      nxv.createdBy.prefix,
      nxv.updatedAt.prefix,
      nxv.updatedBy.prefix,
      nxv.instant.prefix,
      nxv.subject.prefix
    )
  )

  implicit def toPersistence(implicit appConfig: AppConfig): PersistenceConfig      = appConfig.persistence
  implicit def toHttp(implicit appConfig: AppConfig): HttpConfig                    = appConfig.http
  implicit def toPermissionConfig(implicit appConfig: AppConfig): PermissionsConfig = appConfig.permissions
  implicit def toAclsConfig(implicit appConfig: AppConfig): AclsConfig              = appConfig.acls
  implicit def toIndexing(implicit appConfig: AppConfig): IndexingConfig            = appConfig.indexing
}
