package ch.epfl.bluebrain.nexus.iam.core.auth

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import cats.MonadError
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse

/**
  * Downstream authentication provider client which executes the requests and returns successful responses and maps
  * unsuccessful responses to correct error response
  * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
  * @param cl HTTP Client
  */
class DownstreamAuthClient[F[_]](implicit F: MonadError[F, Throwable], cl: UntypedHttpClient[F]) {

  /**
    * Executes the requests and map the response to correct error code we want to respond with.
    * @param  request the request to execute
    * @return the response in an ''F'' context
    */
  def forward(request: HttpRequest): F[HttpResponse] = {
    F.recoverWith(cl(request)) {
      case e @ UnexpectedUnsuccessfulHttpResponse(response) =>
        response.status match {
          case StatusCodes.BadRequest          => F.pure(HttpResponse(StatusCodes.InternalServerError))
          case StatusCodes.Unauthorized        => F.pure(HttpResponse(StatusCodes.Unauthorized))
          case StatusCodes.Forbidden           => F.pure(HttpResponse(StatusCodes.Forbidden))
          case StatusCodes.InternalServerError => F.pure(HttpResponse(StatusCodes.BadGateway))
          case StatusCodes.BadGateway          => F.pure(HttpResponse(StatusCodes.BadGateway))
          case StatusCodes.GatewayTimeout      => F.pure(HttpResponse(StatusCodes.GatewayTimeout))
          case _                               => F.raiseError(e)
        }
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
