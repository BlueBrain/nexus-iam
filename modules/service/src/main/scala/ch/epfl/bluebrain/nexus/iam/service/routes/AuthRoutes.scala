package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.service.auth.DownstreamAuthClient
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import kamon.akka.http.KamonTraceDirectives.traceName

import scala.concurrent.Future

/**
  * HTTP routes for OAuth2 specific functionality
  * @param downstreamClient OIDC provider client
  */
class AuthRoutes(downstreamClient: DownstreamAuthClient[Future]) extends DefaultRoutes("oauth2") {

  def apiRoutes: Route =
    (get & path("authorize") & parameter('redirect.?)) { redirectUri =>
      traceName("authorize") {
        complete(downstreamClient.authorize(redirectUri))
      }
    } ~
      (get & path("token") & parameters(('code, 'state))) { (code, state) =>
        traceName("token") {
          complete(downstreamClient.token(code, state))
        }
      } ~
      (get & path("userinfo")) {
        extractCredentials {
          case Some(credentials: OAuth2BearerToken) =>
            traceName("userinfo") {
              onSuccess(downstreamClient.userInfo(credentials)) { userInfo =>
                complete(StatusCodes.OK -> userInfo)
              }
            }
          case _ => complete(StatusCodes.Unauthorized)
        }
      }
}

object AuthRoutes {
  // $COVERAGE-OFF$
  /**
    * Factory method for oauth2 related routes.
    * @param downstreamClient OIDC provider client
    * @return new instance of AuthRoutes
    */
  def apply(downstreamClient: DownstreamAuthClient[Future]): AuthRoutes = new AuthRoutes(downstreamClient)
  // $COVERAGE-ON$
}
