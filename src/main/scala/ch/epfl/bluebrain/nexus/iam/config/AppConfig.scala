package ch.epfl.bluebrain.nexus.iam.config

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.types.Identity.Group
import ch.epfl.bluebrain.nexus.iam.acls.AccessControlList
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.indexer.retryer.RetryStrategy
import ch.epfl.bluebrain.nexus.service.indexer.retryer.RetryStrategy.Backoff
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives

import scala.concurrent.duration.FiniteDuration

/**
  * Application configuration
  *
  * @param description service description
  * @param http        http interface configuration
  * @param cluster     akka cluster configuration
  * @param persistence persistence configuration
  * @param indexing    Indexing configuration
  */
final case class AppConfig(description: Description,
                           http: HttpConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           indexing: IndexingConfig,
                           initialAcl: InitialAcl)

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
  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

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

  final case class InitialAcl(path: Path, identities: InitialIdentities, permissions: Set[Permission]) {
    private val map: Map[Identity, Set[Permission]] =
      identities.groups.map(Group(_, identities.realm) -> permissions).toMap
    val acl: AccessControlList = AccessControlList(map)
  }

  final case class InitialIdentities(realm: String, groups: Set[String])

//  val iriResolution = Map(
//    tagCtxUri         -> tagCtx,
//    resourceCtxUri    -> resourceCtx,
//    shaclCtxUri       -> shaclCtx,
//    resolverCtxUri    -> resolverCtx,
//    viewCtxUri        -> viewCtx,
//    resolverSchemaUri -> resolverSchema,
//    viewSchemaUri     -> viewSchema
//  )

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
  implicit def inInitialAcl(implicit appConfig: AppConfig): InitialAcl         = appConfig.initialAcl

}
