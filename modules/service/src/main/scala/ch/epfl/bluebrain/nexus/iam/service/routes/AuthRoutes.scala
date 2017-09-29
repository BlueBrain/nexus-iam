package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.Directives._

import scala.collection.immutable
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig

import scala.concurrent.Future

class AuthRoutes(config: OidcConfig)(implicit  cl: UntypedHttpClient[Future]) {


  def routes: Route =
      pathPrefix("oauth2") {
        (get & path("authorize") & parameter('redirect.?)) { redirectUri =>
          val upstreamUri = config.authorizeEndpoint
              .withQuery(Query(redirectUri))
          complete(cl(HttpRequest(uri = upstreamUri)))
        } ~
        (get & path("token") & parameters(('code, 'state))) { (code, state) =>
          val upstreamUri = config.authorizeEndpoint
            .withQuery(Query(
              "code"  -> code,
              "state" -> state))
          complete(cl(HttpRequest(method = HttpMethods.POST, uri = upstreamUri)))
        } ~
        (get & path("userinfo")) {
          optionalHeaderValueByType[Authorization](()) { (authHeader: Option[Authorization]) =>
            val ah = authHeader.toSeq.to[immutable.Seq]
            complete(cl(HttpRequest(uri = config.userinfoEndpoint, headers = ah)))
          }
        }
    }
}

object AuthRoutes {
  // $COVERAGE-OFF$
  def apply(config: OidcConfig)(implicit cl: UntypedHttpClient[Future]): AuthRoutes = {
    new AuthRoutes(config)
  }
}