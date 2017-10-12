package ch.epfl.bluebrain.nexus.iam.oidc.routes

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.model.Uri.{ParsingMode, Query}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import ch.epfl.bluebrain.nexus.iam.oidc.api.Fault.{Rejected, Unauthorized}
import ch.epfl.bluebrain.nexus.iam.oidc.api.Rejection.IllegalRedirectUri
import ch.epfl.bluebrain.nexus.iam.oidc.api.{OidcOps, UserInfo}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

final class AuthRoutes(oidcOps: OidcOps[Future]) {

  private implicit val config: Configuration = Configuration.default.withSnakeCaseKeys

  def routes: Route = {
    pathPrefix("oauth2") {
      extractExecutionContext { implicit ec =>
        (pathPrefix("authorize") & pathEndOrSingleSlash & get) {
          parameter('redirect.?) { redirectString =>
            val eventualMaybeUri = redirectString
              .map(str => Future.fromTry(parseFinalRedirect(str).map(uri => Some(uri))))
              .getOrElse(Future.successful(None))
            val eventualUri = eventualMaybeUri.flatMap(uriOpt => oidcOps.buildRedirectUri(uriOpt))
            onSuccess(eventualUri) { uri =>
              redirect(uri, StatusCodes.Found)
            }
          }
        } ~
          (pathPrefix("token") & pathEndOrSingleSlash & get) {
            (parameter('code) & parameter('state)) { (code, state) =>
              onSuccess(oidcOps.exchangeCode(code, state)) {
                case (token, Some(finalRedirect)) =>
                  val q = ("access_token" -> token.accessToken) +: Query(finalRedirect.rawQueryString)
                  redirect(finalRedirect.withQuery(q), StatusCodes.Found)
                case (token, None) =>
                  complete(Map("access_token" -> token.accessToken))
              }
            }
          } ~
          (pathPrefix("userinfo") & pathEndOrSingleSlash & get) {
            authenticateOAuth2Async[UserInfo]("*", authenticator).apply { userInfo =>
              complete(userInfo)
            }
          }
      }
    }
  }

  private def authenticator(credentials: Credentials)(implicit ec: ExecutionContext): Future[Option[UserInfo]] =
    credentials match {
      case Credentials.Provided(token) =>
        oidcOps
          .getUserInfo(token)
          .map(Some.apply)
          .recoverWith {
            case Unauthorized => Future.successful(None)
          }
      case Credentials.Missing =>
        Future.successful(None)
    }

  private def parseFinalRedirect(value: String): Try[Uri] =
    Try(Uri(value, StandardCharsets.UTF_8, ParsingMode.Strict))
      .filter(uri => uri.isAbsolute && uri.scheme.matches("http(?:s)?"))
      .recoverWith {
        case NonFatal(_) => Failure(Rejected(IllegalRedirectUri))
      }

}

object AuthRoutes {

  final def apply(oidcOps: OidcOps[Future]): AuthRoutes =
    new AuthRoutes(oidcOps)

}
