package ch.epfl.bluebrain.nexus.iam.config

import java.time.Clock

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.util.Timeout
import cats.ApplicativeError
import cats.effect.Timer
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.iam.acls
import ch.epfl.bluebrain.nexus.iam.acls.{AccessControlList, Resource}
import ch.epfl.bluebrain.nexus.iam.config.AppConfig._
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.Identity.{Anonymous, Group}
import ch.epfl.bluebrain.nexus.iam.types.{Identity, Permission, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.{AbsoluteIri, Path}
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.indexer.retryer.RetryStrategy
import ch.epfl.bluebrain.nexus.service.indexer.retryer.RetryStrategy.Backoff
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.sourcing.akka.{AkkaSourcingConfig, PassivationStrategy, RetryStrategy => SourcingRetryStrategy}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Application configuration
  *
  * @param description service description
  * @param http        http interface configuration
  * @param cluster     akka cluster configuration
  * @param persistence persistence configuration
  * @param indexing    indexing configuration
  * @param initialAcl  initial acl configuration (to be considered when the service is first booted)
  * @param permissions sourcing configuration for permissions
  */
final case class AppConfig(description: Description,
                           http: HttpConfig,
                           cluster: ClusterConfig,
                           persistence: PersistenceConfig,
                           indexing: IndexingConfig,
                           initialAcl: InitialAcl,
                           permissions: SourcingConfig)

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
    lazy val aclsIri: AbsoluteIri        = url"$publicUri/$prefix/acls".value
    lazy val permissionsIri: AbsoluteIri = url"$publicUri/$prefix/permissions".value
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

  final case class InitialAcl(path: Path, identities: InitialIdentities, permissions: Set[Permission]) {
    private val map: Map[Identity, Set[Permission]] =
      identities.groups.map(Group(_, identities.realm) -> permissions).toMap

    def acl(implicit c: Clock, http: HttpConfig): Resource =
      ResourceF(http.aclsIri + path.asString,
                1L,
                acls.types,
                c.instant(),
                Anonymous,
                c.instant(),
                Anonymous,
                AccessControlList(map))
  }

  final case class InitialIdentities(realm: String, groups: Set[String])

  /**
    * Partial configuration for aggregate passivation strategy.
    *
    * @param lapsedSinceLastInteraction   duration since last interaction with the aggregate after which the passivation
    *                                     should occur
    * @param lapsedSinceRecoveryCompleted duration since the aggregate recovered after which the passivation should
    *                                     occur
    */
  final case class PassivationStrategyConfig(
      lapsedSinceLastInteraction: Option[FiniteDuration],
      lapsedSinceRecoveryCompleted: Option[FiniteDuration],
  )

  /**
    * Retry strategy configuration.
    *
    * @param strategy     the type of strategy; possible options are "never", "once" and "exponential"
    * @param initialDelay the initial delay before retrying that will be multiplied with the 'factor' for each attempt
    *                     (applicable only for strategy "exponential")
    * @param maxRetries   maximum number of retries in case of failure (applicable only for strategy "exponential")
    * @param factor       the exponential factor (applicable only for strategy "exponential")
    */
  final case class RetryStrategyConfig(
      strategy: String,
      initialDelay: FiniteDuration,
      maxRetries: Int,
      factor: Int
  )

  /**
    * Sourcing configuration.
    *
    * @param askTimeout                        timeout for the message exchange with the aggregate actor
    * @param queryJournalPlugin                the query (read) plugin journal id
    * @param commandEvaluationTimeout          timeout for evaluating commands
    * @param commandEvaluationExecutionContext the execution context where commands are to be evaluated
    * @param shards                            the number of shards for the aggregate
    * @param passivation                       the passivation strategy configuration
    * @param retry                             the retry strategy configuration
    */
  final case class SourcingConfig(
      askTimeout: FiniteDuration,
      queryJournalPlugin: String,
      commandEvaluationTimeout: FiniteDuration,
      commandEvaluationExecutionContext: String,
      shards: Int,
      passivation: PassivationStrategyConfig,
      retry: RetryStrategyConfig,
  ) {

    /**
      * Computes an [[AkkaSourcingConfig]] using an implicitly available actor system.
      *
      * @param as the underlying actor system
      */
    def akkaSourcingConfig(implicit as: ActorSystem): AkkaSourcingConfig =
      AkkaSourcingConfig(
        askTimeout = Timeout(askTimeout),
        readJournalPluginId = queryJournalPlugin,
        commandEvaluationMaxDuration = commandEvaluationTimeout,
        commandEvaluationExecutionContext =
          if (commandEvaluationExecutionContext == "akka") as.dispatcher
          else ExecutionContext.global
      )

    /**
      * Computes a passivation strategy from the provided configuration and the passivation evaluation function.
      *
      * @param shouldPassivate whether aggregate should passivate after a message exchange
      * @tparam State   the type of the aggregate state
      * @tparam Command the type of the aggregate command
      */
    def passivationStrategy[State, Command](
        shouldPassivate: (String, String, State, Option[Command]) => Boolean = (_: String, _: String, _: State, _: Option[Command]) => false
    ): PassivationStrategy[State, Command] =
      PassivationStrategy(
        passivation.lapsedSinceLastInteraction,
        passivation.lapsedSinceRecoveryCompleted,
        shouldPassivate
      )

    /**
      * Computes a retry strategy from the provided configuration.
      */
    def retryStrategy[F[_]: Timer, E](implicit F: ApplicativeError[F, E]): SourcingRetryStrategy[F] =
      retry.strategy match {
        case "exponential" =>
          SourcingRetryStrategy.exponentialBackoff(
            retry.initialDelay,
            retry.maxRetries,
            retry.factor
          )
        case "once" =>
          SourcingRetryStrategy.once
        case _ =>
          SourcingRetryStrategy.never
      }
  }

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
  implicit def inInitialAcl(implicit appConfig: AppConfig): InitialAcl         = appConfig.initialAcl
}
