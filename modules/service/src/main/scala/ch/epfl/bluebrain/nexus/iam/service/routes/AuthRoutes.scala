package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig

import scala.concurrent.Future

class AuthRoutes(config: OidcConfig)(implicit  cl: UntypedHttpClient[Future]) {


  def routes: Route =
      pathPrefix("oauth2") {
        (get & path("authorize") & parameter('redirect.?)) { redirectUri =>
          val upstreamUri = config.authorizeEndpoint
              .withQuery(
                  redirectUri.map(uri => Query("redirect" -> uri)).getOrElse(Query.Empty))
          complete(cl(Get(upstreamUri)))
        } ~
        (get & path("token") & parameters(('code, 'state))) { (code, state) =>
          val upstreamUri = config.tokenEndpoint
            .withQuery(Query(
              "code"  -> code,
              "state" -> state))
          complete(cl(Get(upstreamUri)))
        } ~
        (get & path("userinfo")) {
          headerValueByType[Authorization](()) { authHeader =>
            complete(cl(Get(config.userinfoEndpoint).addHeader(authHeader)))
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