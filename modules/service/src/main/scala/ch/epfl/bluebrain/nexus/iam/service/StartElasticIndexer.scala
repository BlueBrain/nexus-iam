package ch.epfl.bluebrain.nexus.iam.service

import _root_.io.circe.generic.extras.Configuration
import _root_.io.circe.generic.extras.auto._
import _root_.io.circe.java8.time._
import akka.actor.ActorSystem
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event
import ch.epfl.bluebrain.nexus.commons.service.persistence.SequentialTagIndexer
import ch.epfl.bluebrain.nexus.iam.elastic.AclIndexer
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig

import scala.concurrent.{ExecutionContext, Future}

/**
  * Triggers the start of the indexing process from the resumable projection
  *
  * @param settings      the app settings
  * @param elasticClient the ElasticSearch client implementation
  * @param as            the implicitly available [[ActorSystem]]
  * @param ec            the implicitly available [[ExecutionContext]]
  */
class StartElasticIndexer(settings: AppConfig, elasticClient: ElasticClient[Future])(implicit
                                                                                     as: ActorSystem,
                                                                                     ec: ExecutionContext) {

  private implicit val config: Configuration =
    Configuration.default.withDiscriminator("type")

  SequentialTagIndexer.start[Event](
    AclIndexer[Future](elasticClient, settings.elastic).apply _,
    "events-to-es",
    settings.persistence.queryJournalPlugin,
    "permission",
    "event-permission-es-indexer"
  )

}

object StartElasticIndexer {

  // $COVERAGE-OFF$
  /**
    * Constructs a StartElasticIndexers
    *
    * @param settings      the app settings
    * @param elasticClient the ElasticSearch client implementation
    */
  final def apply(settings: AppConfig, elasticClient: ElasticClient[Future])(
      implicit
      as: ActorSystem,
      ec: ExecutionContext): StartElasticIndexer =
    new StartElasticIndexer(settings, elasticClient)

  // $COVERAGE-ON$
}
