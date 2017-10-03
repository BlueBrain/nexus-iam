package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.core.auth.DownstreamAuthClient
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig

import scala.concurrent.Future

/**
  * HTTP routes for OAuth2 specifig functionality
  * @param config           OIDC provider config
  * @param downstreamClient OIDC provider client
  */
class AuthRoutes(config: OidcConfig, downstreamClient: DownstreamAuthClient[Future]) extends DefaultRoutes("oauth2"){

  def apiRoutes: Route =
    (get & path("authorize") & parameter('redirect.?)) { redirectUri =>
      val upstreamUri = config.authorizeEndpoint
        .withQuery(
          redirectUri.map(uri => Query("redirect" -> uri)).getOrElse(Query.Empty))
      complete(downstreamClient.forward(Get(upstreamUri)))
    } ~
    (get & path("token") & parameters(('code, 'state))) { (code, state) =>
      val upstreamUri = config.tokenEndpoint
        .withQuery(Query(
          "code" -> code,
          "state" -> state))
      complete(downstreamClient.forward(Get(upstreamUri)))
     } ~
     (get & path("userinfo")) {
       headerValueByType[Authorization](()) { authHeader =>
         complete(downstreamClient.forward(Get(config.userinfoEndpoint).addHeader(authHeader)))
       }
    }
}

object AuthRoutes {
  // $COVERAGE-OFF$
  /**
    * Factory method for oauth2 related routes.
    * @param config           OIDC provider config
    * @param downstreamClient OIDC provider client
    * @return new instance of AuthRoutes
    */
  def apply(config: OidcConfig, downstreamClient: DownstreamAuthClient[Future]): AuthRoutes = {
    new AuthRoutes(config, downstreamClient)
  }
  // $COVERAGE-ON$
}