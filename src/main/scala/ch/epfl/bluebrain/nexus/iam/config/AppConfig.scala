package ch.epfl.bluebrain.nexus.iam.config

import akka.http.scaladsl.model.Uri
import cats.ApplicativeError
import cats.effect.Timer
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.indexer.retryer.RetryStrategy
import ch.epfl.bluebrain.nexus.service.indexer.retryer.RetryStrategy.Backoff
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.sourcing.akka.{RetryStrategy => SourcingRetryStrategy, SourcingConfig}
import ch.epfl.bluebrain.nexus.sourcing.akka.SourcingConfig.RetryStrategyConfig

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
  */
final case class AppConfig(description: Description,
                           http: HttpConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           indexing: IndexingConfig,
                           acls: AclsConfig,
                           permissions: PermissionsConfig,
                           realms: RealmsConfig)

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
  final case class ClusterConfig(passivationTimeout: FiniteDuration,
                                 replicationTimeout: FiniteDuration,
                                 shards: Int,
                                 seeds: Option[String])

  /**
    * Persistence configuration
    *
    * @param journalPlugin        plugin for storing events
    * @param snapshotStorePlugin  plugin for storing snapshots
    * @param queryJournalPlugin   plugin for querying journal events
    */
  final case class PersistenceConfig(journalPlugin: String, snapshotStorePlugin: String, queryJournalPlugin: String)

  /**
    * Retry configuration with Exponential backoff
    *
    * @param maxCount     the maximum number of times an index function is retried
    * @param maxDuration  the maximum amount of time to wait between two retries
    * @param randomFactor the jitter added between retries
    */
  final case class Retry(maxCount: Int, maxDuration: FiniteDuration, randomFactor: Double) {
    val strategy: RetryStrategy = Backoff(maxDuration, randomFactor)
  }

  /**
    * Indexing configuration
    *
    * @param batch        the maximum number of events taken on each batch
    * @param batchTimeout the maximum amount of time to wait for the number of events to be taken on each batch
    * @param retry        the retry configuration when indexing failures
    */
  final case class IndexingConfig(batch: Int, batchTimeout: FiniteDuration, retry: Retry)

  /**
    * KeyValueStore configuration.
    *
    * @param askTimeout         the maximum duration to wait for the replicator to reply
    * @param consistencyTimeout the maximum duration to wait for a consistent read or write across the cluster
    * @param retry              the retry strategy configuration
    */
  final case class KeyValueStoreConfig(
      askTimeout: FiniteDuration,
      consistencyTimeout: FiniteDuration,
      retry: RetryStrategyConfig,
  ) {
    /**
      * Computes a retry strategy from the provided configuration.
      */
    def retryStrategy[F[_]: Timer, E](implicit F: ApplicativeError[F, E]): SourcingRetryStrategy[F] =
      retry.strategy match {
        case "exponential" =>
          SourcingRetryStrategy.exponentialBackoff(retry.initialDelay, retry.maxRetries, retry.factor)
        case "once" =>
          SourcingRetryStrategy.once
        case _ =>
          SourcingRetryStrategy.never
      }
  }

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

  val orderedKeys = OrderedKeys(
    List(
      "@context",
      "@id",
      "@type",
      "code",
      "message",
      "details",
      nxv.total.prefix,
      nxv.maxScore.prefix,
      nxv.results.prefix,
      nxv.score.prefix,
      "path",
      "",
      nxv.self.prefix,
      nxv.constrainedBy.prefix,
      nxv.project.prefix,
      nxv.createdAt.prefix,
      nxv.createdBy.prefix,
      nxv.updatedAt.prefix,
      nxv.updatedBy.prefix,
      nxv.rev.prefix,
      nxv.deprecated.prefix
    ))

  val tracing = new TracingDirectives()

  implicit def toPersistence(implicit appConfig: AppConfig): PersistenceConfig = appConfig.persistence
  implicit def toHttp(implicit appConfig: AppConfig): HttpConfig               = appConfig.http
  implicit def toIndexing(implicit appConfig: AppConfig): IndexingConfig       = appConfig.indexing
}
