package ch.epfl.bluebrain.nexus.iam

import java.nio.file.Paths

import _root_.io.circe.Json
import akka.actor.{ActorSystem, Address, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, RouteResult}
import akka.stream.ActorMaterializer
import cats.effect.Effect
import cats.effect.concurrent.Deferred
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.iam.acls._
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.permissions.Permissions
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.routes.{AclsRoutes, AppInfoRoutes, CassandraHeath}
import ch.epfl.bluebrain.nexus.service.http.directives.PrefixDirectives._
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

  def bootstrap(as: ActorSystem)(implicit cfg: AppConfig, mt: ActorMaterializer): (Permissions[Task], Acls[Task], Realms[Task]) = {
    implicit val eff: Effect[Task] = Task.catsEffect(Scheduler.global)
    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
    implicit val system = as
    implicit val pc = cfg.permissions
    implicit val ac = cfg.acls
    implicit val rc = cfg.realms
    implicit val pm = CanBlock.permit
    implicit val cl = HttpClient.untyped[Task]
    import as.dispatcher
    implicit val jc = HttpClient.withUnmarshaller[Task, Json]

    val deferred = for {
      ps <- Deferred[Task, Permissions[Task]]
      as <- Deferred[Task, Acls[Task]]
      rs <- Deferred[Task, Realms[Task]]
      pt <- Permissions[Task](() => as.get)
      at <- Acls[Task](() => ps.get)
      rt <- Realms[Task](() => as.get)
      _  <- ps.complete(pt)
      _  <- as.complete(at)
      _  <- rs.complete(rt)
    } yield (pt, at, rt)
    deferred.runSyncUnsafe()(Scheduler.global, pm)
  }

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    val config = loadConfig()
    setupMonitoring(config)

    implicit val appConfig = Settings(config).appConfig

    implicit val hc = appConfig.http
    implicit val as = ActorSystem(appConfig.description.fullName, config)
    implicit val ec = as.dispatcher
    implicit val mt = ActorMaterializer()

    val cluster = Cluster(as)
    val seeds: List[Address] = appConfig.cluster.seeds.toList
      .flatMap(_.split(","))
      .map(addr => AddressFromURIString(s"akka.tcp://${appConfig.description.fullName}@$addr")) match {
      case Nil      => List(cluster.selfAddress)
      case nonEmpty => nonEmpty
    }

    val (perms@_, acls, realms) = bootstrap(as)

    val aclRoutes   = new AclsRoutes(acls, realms).routes
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
