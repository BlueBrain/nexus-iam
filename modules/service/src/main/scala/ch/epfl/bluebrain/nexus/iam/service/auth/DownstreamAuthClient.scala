package ch.epfl.bluebrain.nexus.iam.service.auth

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import cats.MonadError
import cats.syntax.functor._
import cats.syntax.applicativeError._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.core.auth.{User, UserInfo}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig
import io.circe.DecodingFailure
import journal.Logger

import scala.util.control.NonFatal

/**
  * Downstream authentication provider client which executes the requests and returns successful responses and maps
  * unsuccessful responses to correct error response
  * @param config A config object holding the OIDC provider endpoints
  * @param cl     An untyped HTTP client
  * @param uicl   An HTTP Client to fetch a [[UserInfo]] entity
  * @tparam F     the execution mode of the type class, i.e.: __Try__, __Future__
  */
class DownstreamAuthClient[F[_]](config: OidcConfig, cl: UntypedHttpClient[F], uicl: HttpClient[F, UserInfo])(
    implicit F: MonadError[F, Throwable]) {

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
    * Fetches and forwards the [[UserInfo]] object associated to these ''credentials''.
    * @param credentials the OAuth 2.0 bearer token given by the provider
    * @return a [[UserInfo]] instance in an ''F'' context
    */
  def userInfo(credentials: OAuth2BearerToken): F[UserInfo] = {
    uicl(Get(config.userinfoEndpoint).addCredentials(credentials))
      .recoverWith {
        case df: DecodingFailure =>
          log.error("Unable to decode UserInfo response", df)
          F.raiseError(df)
        case UnexpectedUnsuccessfulHttpResponse(resp) =>
          log.error("Received a failed response from the downstream oid provider for getting the UserInfo")
          F.raiseError(UnexpectedUnsuccessfulHttpResponse(mapFailed(resp)))
        case NonFatal(th) =>
          log.error("Downstream call to fetch the UserInfo failed unexpectedly", th)
          F.raiseError(th)
      }
  }

  /**
    * Fetches the ''userinfo'' associated to this access token and builds the corresponding [[User]] instance.
    * @param accessToken the OAuth 2.0 bearer token given by the provider
    * @return a [[User]] holding all the identities the user belongs to in an ''F'' context
    */
  def getUser(accessToken: String): F[User] = {
    userInfo(OAuth2BearerToken(accessToken)).map(_.toUser(config.issuer))
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
      case resp =>
        log.warn(s"""Unexpected status code from OIDC provider ${resp.status} ${request.uri}""")
        mapFailed(resp)
    }
  }

  private def mapFailed(resp: HttpResponse): HttpResponse = resp.status match {
    case Unauthorized                     => HttpResponse(Unauthorized)
    case Forbidden                        => HttpResponse(Forbidden)
    case InternalServerError | BadGateway => HttpResponse(BadGateway)
    case GatewayTimeout                   => HttpResponse(GatewayTimeout)
    case _                                => HttpResponse(InternalServerError)
  }
}

object DownstreamAuthClient {

  /**
    * Factory method to create an DownstreamAuthClient instance
    *
    * @see [[DownstreamAuthClient]]
    */
  def apply[F[_]](config: OidcConfig, cl: UntypedHttpClient[F], uicl: HttpClient[F, UserInfo])(
      implicit F: MonadError[F, Throwable]): DownstreamAuthClient[F] =
    new DownstreamAuthClient(config, cl, uicl)
}
