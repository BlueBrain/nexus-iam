package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.http.{ContextUri, JsonLdCirceSupport}
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo.userInfoEncoder
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization.identityEncoder
import ch.epfl.bluebrain.nexus.iam.core.groups.UsedGroups
import ch.epfl.bluebrain.nexus.iam.service.auth.ClaimExtractor.{JsonSyntax, OAuth2BearerTokenSyntax}
import ch.epfl.bluebrain.nexus.iam.service.auth.{ClaimExtractor, DownstreamAuthClient}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.{ContextConfig, OidcConfig}
import ch.epfl.bluebrain.nexus.iam.service.directives.CredentialsDirectives._
import ch.epfl.bluebrain.nexus.iam.service.groups.UserGroupsOps._
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.Encoder
import kamon.akka.http.KamonTraceDirectives.operationName

import scala.concurrent.{ExecutionContext, Future}

/**
  * HTTP routes for OAuth2 specific functionality
  *
  * @param clients OIDC provider clients
  */
class AuthRoutes(clients: List[DownstreamAuthClient[Future]], usedGroups: UsedGroups[Future])(implicit oidc: OidcConfig,
                                                                                              api: ApiUri,
                                                                                              contexts: ContextConfig,
                                                                                              ce: ClaimExtractor,
                                                                                              ec: ExecutionContext,
                                                                                              orderedKeys: OrderedKeys)
    extends DefaultRoutes("oauth2", contexts.error) {

  private implicit val enc: Encoder[Identity] = identityEncoder(api.base)
  private implicit val iamContext: ContextUri = contexts.iam

  def apiRoutes: Route =
    (get & path("authorize") & parameter('redirect.?) & parameter('realm ? oidc.defaultRealm)) { (redirectUri, realm) =>
      operationName("authorize") {
        clients
          .findByRealm(realm)
          .fold(complete(StatusCodes.NotFound))(cl => complete(cl.authorize(redirectUri)))
      }
    } ~
      (get & pathPrefix("token") & pathPrefix(Segment) & parameters(('code, 'state))) { (realm, code, state) =>
        operationName("token") {
          clients
            .findByRealm(realm)
            .fold(complete(StatusCodes.NotFound))(cl => complete(cl.token(code, state)))
        }
      } ~
      (get & path("userinfo") & extractBearerToken) { credentials =>
        operationName("userinfo") {
          complete {
            credentials.extractClaim.flatMap {
              case (client, json) =>
                json.extractUserInfo.recoverWith {
                  case _ => client.userInfo(credentials)
                }
            }
          }
        }
      } ~
      (get & path("user") & extractBearerToken & parameter('filterGroups.as[Boolean] ? false)) {
        import JsonLdCirceSupport.marshallerHttp

        (credentials, filterGroups) =>
          operationName("user") {
            complete {
              credentials.extractClaim.flatMap {
                case (client, json) =>
                  val user = json
                    .extractUser(client.config)
                    .recoverWith {
                      case _ => client.getUser(credentials)
                    }
                  if (filterGroups) {
                    val realmUsedGroups = usedGroups.fetch(client.config.realm)
                    user product realmUsedGroups map { case (u, groups) => u.filterGroups(groups) }
                  } else user

              }
            }
          }
      }
}

object AuthRoutes {
  // $COVERAGE-OFF$
  /**
    * Factory method for oauth2 related routes.
    *
    * @param clients OIDC provider clients
    * @return new instance of AuthRoutes
    */
  def apply(clients: List[DownstreamAuthClient[Future]], usedGroups: UsedGroups[Future])(
      implicit oidc: OidcConfig,
      api: ApiUri,
      contexts: ContextConfig,
      ce: ClaimExtractor,
      ec: ExecutionContext,
      orderedKeys: OrderedKeys): AuthRoutes =
    new AuthRoutes(clients, usedGroups: UsedGroups[Future])

  // $COVERAGE-ON$
}
