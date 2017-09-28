package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.service.types._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import kamon.akka.http.KamonTraceDirectives.traceName

/**
  * Akka HTTP route definition for things that can be considered static, namely:
  * <ul>
  * <li>The service description</li>
  * <li>The service specification</li>
  * <li>The service specification browser</li>
  * </ul>
  */
class StaticRoutes(serviceDescription: ServiceDescription, publicUri: Uri, apiPrefix: String) {

  private val prefix = prefixOf(publicUri)

  def routes: Route = pathPrefix(prefix) {
    get {
      pathEndOrSingleSlash {
        traceName("serviceDescription") {
          complete(Boxed(serviceDescription, List(Link("api", s"$publicUri/$apiPrefix/acls"))))
        }
      }
    }
  }
}

object StaticRoutes {

  /**
    * Default factory method for building [[ch.epfl.bluebrain.nexus.iam.service.routes.StaticRoutes]] instances.
    *
    * @param name    the name of the service
    * @param version the current version of the service
    * @param publicUri the publicUri of the service
    * @param prefix the path prefix to the service api
    * @return a new [[ch.epfl.bluebrain.nexus.iam.service.routes.StaticRoutes]] instance
    */
  def apply(name: String, version: String, publicUri: Uri, prefix: String): StaticRoutes = {
    val uri = publicUri.copy(rawQueryString = None, fragment = None)
    new StaticRoutes(ServiceDescription(name, version), uri, prefix)
  }
}
