package ch.epfl.bluebrain.nexus.iam.oidc.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.oidc.config.Settings
import ch.epfl.bluebrain.nexus.iam.oidc.routes.StaticRoutes.ServiceDescription
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import kamon.akka.http.KamonTraceDirectives.traceName

/**
  * Http route definitions that have constant outcomes per runtime.
  *
  * @param desc the service description
  */
class StaticRoutes(desc: ServiceDescription) {

  def routes: Route = pathEndOrSingleSlash {
    get {
      traceName("serviceDescription") {
        complete(StatusCodes.OK -> desc)
      }
    }
  }
}

object StaticRoutes {

  /**
    * Constructs a new ''StaticRoutes'' instance that defines the static http routes of the service.
    *
    * @param as an implicitly available actor system
    * @return a new ''StaticRoutes'' instance
    */
  final def apply()(implicit as: ActorSystem): StaticRoutes = {
    val desc        = Settings(as).appConfig.description
    val description = ServiceDescription(desc.name, desc.version, desc.environment)
    new StaticRoutes(description)
  }

  /**
    * Local data type that wraps service information.
    *
    * @param name    the name of the service
    * @param version the version of the service
    * @param env     the environment in which the service is run
    */
  final case class ServiceDescription(name: String, version: String, env: String)
}
