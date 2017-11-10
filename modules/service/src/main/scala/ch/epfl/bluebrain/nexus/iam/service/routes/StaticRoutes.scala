package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.types._
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

  private def serviceDescriptionRoute: Route = pathEndOrSingleSlash {
    get {
      traceName("serviceDescription") {
        complete(Boxed(serviceDescription, List(Link("api", s"$publicUri/$apiPrefix/acls"))))
      }
    }
  }
  private def docsRoute =
    pathPrefix("docs") {
      pathPrefix("iam") {
        pathEndOrSingleSlash {
          redirectToTrailingSlashIfMissing(StatusCodes.MovedPermanently) {
            getFromResource("docs/index.html")
          }
        } ~
          getFromResourceDirectory("docs")
      }
    }

  def routes: Route = serviceDescriptionRoute ~ docsRoute

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
