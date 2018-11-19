package ch.epfl.bluebrain.nexus.iam

import java.nio.file.Paths
import java.time.Clock

import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.iam.acls._
import ch.epfl.bluebrain.nexus.iam.config.Settings
import ch.epfl.bluebrain.nexus.iam.index.{AclsIndex, InMemoryAclsTree}
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.routes.{AclsRoutes, AppInfoRoutes, CassandraHeath}
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives._
import ch.epfl.bluebrain.nexus.sourcing.akka.AkkaSourcingConfig
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.{cors, corsRejectionHandler}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.github.jsonldjava.core.DocumentLoader
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.system.SystemMetrics
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.CanBlock

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection TypeAnnotation
// $COVERAGE-OFF$
object Main {

  def loadConfig(): Config = {
    val cfg = sys.env.get("IAM_CONFIG_FILE") orElse sys.props.get("iam.config.file") map { str =>
      val file = Paths.get(str).toAbsolutePath.toFile
      ConfigFactory.parseFile(file)
    } getOrElse ConfigFactory.empty()
    (cfg withFallback ConfigFactory.load()).resolve()
  }

  def setupMonitoring(config: Config): Unit = {
    Kamon.reconfigure(config)
    SystemMetrics.startCollecting()
    Kamon.loadReportersFromConfig()
  }

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    val config = loadConfig()
    setupMonitoring(config)

    implicit val appConfig = Settings(config).appConfig

    implicit val as = ActorSystem(appConfig.description.fullName, config)
    implicit val ec = as.dispatcher
    implicit val mt = ActorMaterializer()
    implicit val pm = CanBlock.permit
    implicit val cl = Clock.systemUTC()

    val cluster = Cluster(as)
    val seeds: List[Address] = appConfig.cluster.seeds.toList
      .flatMap(_.split(","))
      .map(addr => AddressFromURIString(s"akka.tcp://${appConfig.description.fullName}@$addr")) match {
      case Nil      => List(cluster.selfAddress)
      case nonEmpty => nonEmpty
    }

    //TODO: Take this from the AppConfig
    implicit val aclsSourcingConfig: AkkaSourcingConfig = AkkaSourcingConfig(
      Timeout(3 seconds),
      appConfig.persistence.queryJournalPlugin,
      3 seconds,
      as.dispatcher
    )

    val aclsIndex: AclsIndex[Task] = InMemoryAclsTree.task()

    val acls: Acls[Task] = {
      implicit val sc = Scheduler.global
      Acls[Task](aclsIndex).runSyncUnsafe()
    }

    val aclRoutes   = new AclsRoutes(acls, new Realms()).routes
    val apiRoutes   = uriPrefix(appConfig.http.publicUri)(aclRoutes)
    val serviceDesc = AppInfoRoutes(appConfig.description, cluster, CassandraHeath(as)).routes

    val logger = Logging(as, getClass)
    System.setProperty(DocumentLoader.DISALLOW_REMOTE_CONTEXT_LOADING, "true")

    val corsSettings = CorsSettings.defaultSettings
      .withAllowedMethods(List(GET, PUT, POST, DELETE, OPTIONS, HEAD))
      .withExposedHeaders(List(Location.name))

    cluster.registerOnMemberUp {
      logger.info("==== Cluster is Live ====")

      val routes: Route =
        handleRejections(corsRejectionHandler)(cors(corsSettings)(apiRoutes ~ serviceDesc))

      val httpBinding = {
        Http().bindAndHandle(RouteResult.route2HandlerFlow(routes), appConfig.http.interface, appConfig.http.port)
      }
      httpBinding onComplete {
        case Success(binding) =>
          logger.info(s"Bound to ${binding.localAddress.getHostString}: ${binding.localAddress.getPort}")
        case Failure(th) =>
          logger.error(th, "Failed to perform an http binding on {}:{}", appConfig.http.interface, appConfig.http.port)
          Await.result(as.terminate(), 10 seconds)
      }
    }

    cluster.joinSeedNodes(seeds)

    as.registerOnTermination {
      cluster.leave(cluster.selfAddress)
      Kamon.stopAllReporters()
      SystemMetrics.stopCollecting()
    }
    // attempt to leave the cluster before shutting down
    val _ = sys.addShutdownHook {
      Await.result(as.terminate().map(_ => ())(as.dispatcher), 10 seconds)
    }
  }
}
// $COVERAGE-ON$
