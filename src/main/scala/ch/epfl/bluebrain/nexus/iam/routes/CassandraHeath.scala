package ch.epfl.bluebrain.nexus.iam.routes

import akka.Done
import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.event.Logging
import akka.persistence.cassandra.CassandraPluginConfig
import akka.persistence.cassandra.session.scaladsl.CassandraSession

import scala.concurrent.Future

trait CassandraHeath extends Extension {

  /**
    * Performs a query against cassandra DB to check the connectivity.
    *
    * @return Future(true) when cassandra DB is accessible from within the app
    *         Future(false) otherwise
    */
  def check: Future[Boolean]
}

object CassandraHeath extends ExtensionId[CassandraHeath] with ExtensionIdProvider {

  override def lookup(): ExtensionId[_ <: Extension] = CassandraHeath

  override def createExtension(as: ExtendedActorSystem): CassandraHeath = {
    implicit val ec = as.dispatcher
    val log         = Logging(as, "CassandraHeathCheck")
    val config      = new CassandraPluginConfig(as, as.settings.config.getConfig("cassandra-journal"))
    val (p, s)      = (config.sessionProvider, config.sessionSettings)
    val session     = new CassandraSession(as, p, s, ec, log, "health", _ => Future.successful(Done.done()))

    new CassandraHeath {
      private val query = s"SELECT now() FROM ${config.keyspace}.messages;"

      override def check: Future[Boolean] = {
        session.selectOne(query).map(_ => true).recover {
          case err =>
            log.error("Error while attempting to query for health check", err)
            false
        }
      }
    }
  }
}
