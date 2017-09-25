package ch.epfl.bluebrain.nexus.iam.service

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.iam.service.config.Settings
import ch.epfl.bluebrain.nexus.iam.service.routes.CustomRejectionHandler.instance
import ch.epfl.bluebrain.nexus.iam.service.routes.StaticRoutes
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.ConfigFactory
import kamon.Kamon

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.util.Success

// $COVERAGE-OFF$
object Main {

  @SuppressWarnings(Array("UnusedMethodParameter"))
  def main(args: Array[String]): Unit = {
    Kamon.start()

    // generic implicits
    val config    = ConfigFactory.load()
    val appConfig = new Settings(config).appConfig

    implicit val as: ActorSystem              = ActorSystem(appConfig.description.ActorSystemName, config)
    implicit val ec: ExecutionContextExecutor = as.dispatcher
    implicit val mt: ActorMaterializer        = ActorMaterializer()

    val logger = Logging(as, getClass)
    val corsSettings = CorsSettings.defaultSettings
      .copy(allowedMethods = List(GET, PUT, POST, DELETE, OPTIONS, HEAD), exposedHeaders = List(Location.name))

    val cluster = Cluster(as)

    // cluster join hook
    cluster.registerOnMemberUp({
      logger.info("==== Cluster is Live ====")

      // configure routes
      val staticRoutes = StaticRoutes(appConfig.description.name,
                                      appConfig.description.version,
                                      appConfig.http.publicUri,
                                      appConfig.http.prefix).routes

      val route = handleRejections(corsRejectionHandler) {
        cors(corsSettings)(staticRoutes)
      }

      // bind to http
      Http().bindAndHandle(route, appConfig.http.interface, appConfig.http.port) onComplete {
        case Success(binding) =>
          logger.info(s"Bound to '${binding.localAddress.getHostString}': '${binding.localAddress.getPort}'")
        case _ => Await.result(as.terminate(), 3.seconds)
      }
    })

    val provided = appConfig.cluster.seedAddresses
      .map(addr => AddressFromURIString(s"akka.tcp://${appConfig.description.ActorSystemName}@$addr"))
    val seeds = if (provided.isEmpty) Set(cluster.selfAddress) else provided

    cluster.joinSeedNodes(seeds.toList)

    as.registerOnTermination {
      cluster.leave(cluster.selfAddress)
      Kamon.shutdown()
    }
    // attempt to leave the cluster before shutting down
    val _ = sys.addShutdownHook {
      val _ = Await.result(as.terminate(), 5.seconds)
    }
  }
}
// $COVERAGE-ON$