package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import java.util.UUID

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.pattern.{AskTimeoutException, ask}
import akka.stream.Materializer
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
import ch.epfl.bluebrain.nexus.iam.oidc.api.Fault._
import ch.epfl.bluebrain.nexus.iam.oidc.api.IdAccessToken._
import ch.epfl.bluebrain.nexus.iam.oidc.api.Rejection.AuthorizationAttemptWithInvalidState
import ch.epfl.bluebrain.nexus.iam.oidc.api.{IdAccessToken, OidcOps}
import ch.epfl.bluebrain.nexus.iam.oidc.config.AppConfig.OidcConfig
import ch.epfl.bluebrain.nexus.iam.oidc.config.OidcProviderConfig
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.{Decoder, DecodingFailure}
import journal.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * A default [[ch.epfl.bluebrain.nexus.iam.oidc.api.OidcOps]] implementation that uses Akka actors to maintain the
  * state and user information.
  *
  * @param stateRef    a reference to a state actor (either a router or a cluster shard)
  * @param userInfoRef a reference to a user information actor (either a router or a cluster shard)
  * @param cfg         the oidc configuration
  * @param pcfg        the provider oidc configuration
  */
class ShardedOidcOps(stateRef: ActorRef, userInfoRef: ActorRef, cfg: OidcConfig, pcfg: OidcProviderConfig)(
    implicit ec: ExecutionContext,
    mt: Materializer,
    tm: Timeout,
    cl: UntypedHttpClient[Future],
    userInfoDec: Decoder[UserInfo])
    extends OidcOps[Future] {

  private val log = Logger[this.type]

  protected implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  private val iaClient = HttpClient.withAkkaUnmarshaller[IdAccessToken]
  private val uiClient = HttpClient.withAkkaUnmarshaller[UserInfo]

  protected def generateStateId(): String =
    UUID.randomUUID().toString.toLowerCase

  override def buildRedirectUri(finalRedirect: Option[Uri]): Future[Uri] = {
    import ch.epfl.bluebrain.nexus.iam.oidc.defaults.StateActor.Protocol._
    val freshUuid = generateStateId()
    (stateRef ? GenState(freshUuid, finalRedirect))
      .flatMap {
        case state: AuthState =>
          Future.successful(
            pcfg.authorization.withQuery(
              Query(
                "response_type" -> "code",
                "client_id"     -> cfg.clientId,
                "redirect_uri"  -> cfg.tokenWithRealm.toString(),
                "scope"         -> cfg.scopes.mkString(" "),
                "state"         -> state.uuid
              )))
        // $COVERAGE-OFF$
        case msg =>
          log.error(s"Received an unexpected message '$msg' while waiting for a fresh redirect uri")
          Future.failed(Unexpected("Received an unexpected message while waiting for a fresh redirect uri", msg))
        // $COVERAGE-ON$
      }
      .recoverWith {
        // $COVERAGE-OFF$
        case _: AskTimeoutException =>
          log.error("Timed out while waiting for the ack when computing the redirect uri")
          Future.failed(TimedOut("Timed out while waiting for a redirect uri"))
        // $COVERAGE-ON$
      }
  }

  override def exchangeCode(code: String, state: String): Future[(IdAccessToken, Option[Uri])] = {
    import ch.epfl.bluebrain.nexus.iam.oidc.defaults.StateActor.Protocol._
    def validateState: Future[AuthState] =
      (stateRef ? ValidateState(state))
        .flatMap {
          case as: AuthState =>
            log.debug(s"State validation succeeded for '$as'")
            Future.successful(as)
          case _: InvalidStateReference =>
            log.debug(s"State validation failed for state '$state'")
            Future.failed(Rejected(AuthorizationAttemptWithInvalidState))
        }
        .recoverWith {
          // $COVERAGE-OFF$
          case _: AskTimeoutException =>
            log.error(s"Timed out while waiting for the state '$state' validation reply")
            Future.failed(TimedOut(s"Timed out while waiting for the state '$state' validation reply"))
          // $COVERAGE-ON$
        }
    def executeExchange: Future[IdAccessToken] = {
      val data = FormData(
        "code"          -> code,
        "client_id"     -> cfg.clientId,
        "client_secret" -> cfg.clientSecret,
        "redirect_uri"  -> cfg.tokenWithRealm.toString(),
        "grant_type"    -> "authorization_code"
      )
      iaClient(HttpRequest(HttpMethods.POST, pcfg.token, entity = data.toEntity))
        .recoverWith {
          case df: DecodingFailure =>
            log.error("Unable to decode IdAccessToken response", df)
            Future.failed(UnsuccessfulDownstreamCall("Unable to decode IdAccessToken reply", df))
          case ur: UnexpectedUnsuccessfulHttpResponse =>
            log.error("Received an unexpected response from the downstream oidc provider", ur)
            Future.failed(UnsuccessfulDownstreamCall("Received a failed response from the downstream oid provider", ur))
          case NonFatal(th) =>
            log.error("The http client returned an exception when exchanging the code for an access token", th)
            Future.failed(UnsuccessfulDownstreamCall("Downstream call failed unexpectedly", th))
        }
    }

    for {
      AuthState(_, finalRedirect) <- validateState
      idAccessToken               <- executeExchange
    } yield (idAccessToken, finalRedirect)
  }

  override def getUserInfo(accessToken: String): Future[UserInfo] = {
    import ch.epfl.bluebrain.nexus.iam.oidc.defaults.UserInfoActor.Protocol._
    def lookup: Future[Option[UserInfo]] =
      (userInfoRef ? GetInfo(accessToken))
        .flatMap {
          case info: Info =>
            log.debug("Looked up cached user info")
            Future.successful(info.userInfo)
          // $COVERAGE-OFF$
          case other =>
            log.error(s"Cached user info lookup returned an unexpected message '$other'")
            Future.failed(Unexpected("Received an unexpected message while waiting for the user info", other))
          // $COVERAGE-ON$
        }
        .recoverWith {
          // $COVERAGE-OFF$
          case _: AskTimeoutException =>
            log.error("Timed out while waiting for the cached user info")
            Future.failed(TimedOut("Timed out while waiting for the cached user info"))
          case NonFatal(th) =>
            log.error("Exception caught while fetching the cached user info", th)
            Future.failed(InternalFault("Exception caught while fetching the cached user info", th))
          // $COVERAGE-ON$
        }
    def fetch: Future[UserInfo] = {
      val req = HttpRequest(HttpMethods.GET, uri = pcfg.userInfo).addCredentials(OAuth2BearerToken(accessToken))
      uiClient(req).recoverWith {
        case df: DecodingFailure =>
          log.error("Unable to decode UserInfo response", df)
          Future.failed(UnsuccessfulDownstreamCall("Unable to decode UserInfo response", df))
        case ur: UnexpectedUnsuccessfulHttpResponse if ur.response.status == StatusCodes.Unauthorized =>
          log.error("UserInfo retrieval failed, the caller is not authorized", ur)
          Future.failed(Unauthorized)
        case ur: UnexpectedUnsuccessfulHttpResponse =>
          log.error("Received a failed response from the downstream oid provider for getting the UserInfo", ur)
          Future.failed(
            UnsuccessfulDownstreamCall(
              "Received a failed response from the downstream oid provider for getting the UserInfo",
              ur))
        case NonFatal(th) =>
          log.error("Downstream call to fetch the UserInfo failed unexpectedly", th)
          Future.failed(UnsuccessfulDownstreamCall("Downstream call to fetch the UserInfo failed unexpectedly", th))
      }
    }
    def set(userInfo: UserInfo): Future[UserInfo] = {
      userInfoRef ! SetInfo(accessToken, userInfo)
      Future.successful(userInfo)
    }
    lookup flatMap {
      case Some(info) => Future.successful(info)
      case None       => fetch flatMap set
    }
  }
}
