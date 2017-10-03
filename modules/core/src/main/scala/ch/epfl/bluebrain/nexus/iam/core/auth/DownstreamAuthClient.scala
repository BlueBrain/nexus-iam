package ch.epfl.bluebrain.nexus.iam.core.auth

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import cats.MonadError
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse
import journal.Logger

/**
  * Downstream authentication provider client which executes the requests and returns successful responses and maps
  * unsuccessful responses to correct error response
  * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
  * @param cl HTTP Client
  */
class DownstreamAuthClient[F[_]](implicit F: MonadError[F, Throwable], cl: UntypedHttpClient[F]) {

  private val log = Logger[this.type]

  /**
    * Executes the requests and map the response to correct error code we want to respond with.
    *
    * @param  request the request to execute
    * @return the response in an ''F'' context
    */
  def forward(request: HttpRequest): F[HttpResponse] = {
    F.map(cl(request)) { response =>
      if (response.status.isSuccess()) {
        response
      } else {
        logAndMapStatusCode(request, response)
      }
    }
  }

  private def logAndMapStatusCode(request: HttpRequest, response: HttpResponse): HttpResponse = {
    log.warn(s"""Unexpected status code from OIDC provider ${response.status} ${request.uri}""")
    response.status match {
      case BadRequest                       => HttpResponse(InternalServerError)
      case Unauthorized                     => HttpResponse(Unauthorized)
      case Forbidden                        => HttpResponse(Forbidden)
      case InternalServerError | BadGateway => HttpResponse(BadGateway)
      case GatewayTimeout                   => HttpResponse(GatewayTimeout)
      case _                                => throw UnexpectedUnsuccessfulHttpResponse(response)
    }
  }
}

object DownstreamAuthClient {

  /**
    * Factory method to create an DownstreamAuthClient instance
    *
    * @see [[DownstreamAuthClient]]
    */
  def apply[F[_]]()(implicit F: MonadError[F, Throwable], cl: UntypedHttpClient[F]): DownstreamAuthClient[F] =
    new DownstreamAuthClient()
}
