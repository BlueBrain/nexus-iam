package ch.epfl.bluebrain.nexus.iam.service

import _root_.io.circe.generic.extras.Configuration
import _root_.io.circe.generic.extras.auto._
import _root_.io.circe.java8.time._
import akka.actor.{ActorRef, ActorSystem}
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event
import ch.epfl.bluebrain.nexus.iam.elastic.AclIndexer
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig
import ch.epfl.bluebrain.nexus.service.indexer.persistence.SequentialTagIndexer

import scala.concurrent.{ExecutionContext, Future}

object StartElasticIndexer {

  // $COVERAGE-OFF$
  /**
    * Triggers the start of the indexing process from the resumable projection
    *
    * @param settings      the app settings
    * @param elasticClient the ElasticSearch client implementation
    */
  final def apply(settings: AppConfig, elasticClient: ElasticClient[Future])(implicit
                                                                             as: ActorSystem,
                                                                             ec: ExecutionContext): ActorRef = {
    implicit val config: Configuration = Configuration.default.withDiscriminator("type")
    SequentialTagIndexer.start[Event](
      AclIndexer[Future](elasticClient, settings.elastic).apply _,
      "events-to-es",
      settings.persistence.queryJournalPlugin,
      "permission",
      "event-permission-es-indexer"
    )
  }

  // $COVERAGE-ON$
}
