package ch.epfl.bluebrain.nexus.iam.service.auth

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.identity.User
import ch.epfl.bluebrain.nexus.iam.core.acls.types.UserInfo
import ch.epfl.bluebrain.nexus.iam.service.auth.AuthenticationFailure.{
  UnauthorizedCaller,
  UnexpectedAuthenticationFailure
}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcProviderConfig
import io.circe.DecodingFailure
import journal.Logger
import shapeless.Typeable

import scala.util.control.NonFatal

/**
  * Downstream authentication provider client which executes the requests and returns successful responses and maps
  * unsuccessful responses to correct error response
  * @param cl     An untyped HTTP client
  * @param uicl   An HTTP Client to fetch a [[UserInfo]] entity
  * @tparam F     the execution mode of the type class, i.e.: __Try__, __Future__
  */
class DownstreamAuthClient[F[_]](cl: UntypedHttpClient[F],
                                 uicl: HttpClient[F, UserInfo],
                                 val config: OidcProviderConfig)(implicit F: MonadError[F, Throwable]) {

  private val log = Logger[this.type]

  /**
    * Calls the ''authorize'' endpoint and redirects the client to the the provided callback URI, if present.
    * @param redirectUri the callback URI to redirect to after a successful authentication
    * @return the response in an ''F'' context
    */
  def authorize(redirectUri: Option[String]): F[HttpResponse] = redirectUri match {
    case Some(uri) => forward(Get(config.authorizeEndpoint.withQuery(Query("redirect" -> uri))))
    case None      => forward(Get(config.authorizeEndpoint))
  }

  /**
    * Forwards the OAuth 2.0 access token received from the provider.
    * @param code the code received from the provider after a successful authentication
    * @param state the internal value used to maintain state between the authentication request and callback
    * @see [[authorize]]
    * @return the response in an ''F'' context
    */
  def token(code: String, state: String): F[HttpResponse] =
    forward(Get(config.tokenEndpoint.withQuery(Query("code" -> code, "state" -> state))))

  /**
    * Forwards the ''userinfo'' object received from the provider for these ''credentials''.
    * @param credentials the OAuth 2.0 bearer token given by the provider
    * @return the response in an ''F'' context
    */
  def userInfo(credentials: OAuth2BearerToken): F[UserInfo] =
    uicl(Get(config.userinfoEndpoint).addCredentials(credentials))
      .recoverWith(recover[UserInfo])

  /**
    * Fetches the ''userinfo'' associated to this access token and builds the corresponding [[User]] instance.
    * @param credentials the OAuth 2.0 bearer token given by the provider
    * @return a [[User]] holding all the identities the user belongs to, in an ''F'' context
    */
  def getUser(credentials: OAuth2BearerToken): F[User] =
    userInfo(credentials).map(_.toUser(config.realm))

  private def recover[A](implicit T: Typeable[A]): PartialFunction[Throwable, F[A]] = {
    case df: DecodingFailure =>
      log.error(s"Unable to decode '${T.describe}' response", df)
      F.raiseError(UnexpectedAuthenticationFailure(df))
    case UnexpectedUnsuccessfulHttpResponse(HttpResponse(status, _, _, _)) =>
      val cause = if (status == StatusCodes.Unauthorized) {
        log.info(s"Credentials were rejected by the OIDC provider $status ${config.userinfoEndpoint}")
        UnauthorizedCaller
      } else {
        log.error(s"Unexpected status code from OIDC provider $status ${config.userinfoEndpoint}")
        UnexpectedAuthenticationFailure(UnexpectedUnsuccessfulHttpResponse(HttpResponse(mapErrorCode(status))))
      }
      F.raiseError(cause)
    case NonFatal(th) =>
      log.error(s"Downstream call to fetch the '${T.describe}' failed unexpectedly", th)
      F.raiseError(UnexpectedAuthenticationFailure(th))
  }

  /**
    * Executes the requests and map the response to correct error code we want to respond with.
    *
    * @param  request the request to execute
    * @return the response in an ''F'' context
    */
  protected[auth] def forward(request: HttpRequest): F[HttpResponse] = {
    cl(request) map {
      case resp if resp.status.isSuccess => resp
      case resp if resp.status == StatusCodes.Unauthorized =>
        log.info(s"Credentials were rejected by the OIDC provider ${resp.status} ${request.uri}")
        mapFailedResponse(resp)
      case resp =>
        log.error(s"Unexpected status code from OIDC provider ${resp.status} ${request.uri}")
        mapFailedResponse(resp)
    }
  }

  private def mapFailedResponse(resp: HttpResponse): HttpResponse = HttpResponse(mapErrorCode(resp.status))

  private def mapErrorCode(status: StatusCode): StatusCode = status match {
    case Unauthorized                     => Unauthorized
    case Forbidden                        => Forbidden
    case InternalServerError | BadGateway => BadGateway
    case GatewayTimeout                   => GatewayTimeout
    case _                                => InternalServerError
  }
}

object DownstreamAuthClient {

  /**
    * Factory method to create an DownstreamAuthClient instance
    *
    * @see [[DownstreamAuthClient]]
    */
  def apply[F[_]](cl: UntypedHttpClient[F], uicl: HttpClient[F, UserInfo], config: OidcProviderConfig)(
      implicit F: MonadError[F, Throwable]): DownstreamAuthClient[F] =
    new DownstreamAuthClient(cl, uicl, config)
}
