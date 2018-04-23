package ch.epfl.bluebrain.nexus.iam.oidc

import akka.actor.{ActorSystem, AddressFromURIString}
import akka.cluster.Cluster
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.core.acls.types.UserInfo
import ch.epfl.bluebrain.nexus.iam.oidc.config.{OidcProviderConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.{ShardedOidcOps, StateActor, UserInfoActor}
import ch.epfl.bluebrain.nexus.iam.oidc.routes.{AuthRoutes, ExceptionHandling, RejectionHandling, StaticRoutes}
import com.typesafe.config.ConfigFactory
import io.circe.Decoder

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.Success

// $COVERAGE-OFF$
/**
  * Initializes the IAM Proxy service
  *
  * @param preStart an optional function that gets called before starting the service
  * @param postStop an optional function that gets called after stopping the service
  * @param D        the implicitly available decoder for ''UserInfo''
  * @tparam A the generic type response of the ''preStart'' and ''postStop'' functions
  */
class BootstrapService[A](preStart: Option[() => A] = None, postStop: Option[() => A])(
    implicit val D: Decoder[UserInfo]) {

  preStart.map(_.apply())
  // generic implicits
  val config    = ConfigFactory.load()
  val appConfig = new Settings(config).appConfig

  implicit val as: ActorSystem               = ActorSystem(appConfig.description.ActorSystemName, config)
  implicit val ec: ExecutionContextExecutor  = as.dispatcher
  implicit val mt: ActorMaterializer         = ActorMaterializer()
  implicit val tm: Timeout                   = Timeout(appConfig.runtime.defaultTimeout)
  implicit val cl: UntypedHttpClient[Future] = HttpClient.akkaHttpClient

  val logger = Logging(as, getClass)

  val cluster = Cluster(as)

  cluster.registerOnMemberUp {
    // boostrap shards
    val state    = StateActor()
    val userInfo = UserInfoActor()

    val futureBinding = for {
      // fetch config and create ops
      providerConfig <- OidcProviderConfig(appConfig.oidc.discoveryUri)
      ops = new ShardedOidcOps(state, userInfo, appConfig.oidc, providerConfig)

      // configure routes
      staticRoutes = StaticRoutes().routes
      authRoutes   = AuthRoutes(ops).routes
      routes = handleExceptions(ExceptionHandling.exceptionHandler) {
        handleRejections(RejectionHandling.rejectionHandler) {
          authRoutes ~ staticRoutes
        }
      }

      // bind to http
      binding <- Http().bindAndHandle(routes, appConfig.http.interface, appConfig.http.port)
    } yield binding

    futureBinding onComplete {
      case Success(binding) =>
        logger.info(s"Bound to '${binding.localAddress.getHostString}': '${binding.localAddress.getPort}'")
      case _ => Await.result(as.terminate(), 10.seconds)
    }
  }

  val provided = appConfig.cluster.seedAddresses
    .map(addr => AddressFromURIString(s"akka.tcp://${appConfig.description.ActorSystemName}@$addr"))
  val seeds = if (provided.isEmpty) Set(cluster.selfAddress) else provided

  cluster.joinSeedNodes(seeds.toList)

  as.registerOnTermination {
    cluster.leave(cluster.selfAddress)
    postStop.map(_.apply())
  }
  // attempt to leave the cluster before shutting down
  val _ = sys.addShutdownHook {
    val _ = Await.result(as.terminate(), 5.seconds)
  }

}

object BootstrapService {

  /**
    * Constructs a ''BootstrapService'' without prestart or poststop functions
    *
    * @param D the implicitly available ''UserInfo''
    */
  final def apply(implicit D: Decoder[UserInfo]): BootstrapService[None.type] = new BootstrapService(None, None)

  /**
    * Constructs a ''BootstrapService'' with prestart and poststop functions
    *
    * @param preStart the prestart function
    * @param postStop the poststop function
    * @param D        the implicitly available decoder for ''UserInfo''
    * @tparam A the generic type response of the prestart and poststop functions
    */
  final def apply[A](preStart: () => A, postStop: () => A)(implicit D: Decoder[UserInfo]): BootstrapService[A] =
    new BootstrapService(Some(preStart), Some(postStop))

}
// $COVERAGE-ON$
