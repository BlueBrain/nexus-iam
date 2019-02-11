package ch.epfl.bluebrain.nexus.iam.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.alpakka.sse.scaladsl.{EventSource => SSESource}
import akka.stream.scaladsl.Source
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._
import io.circe.Decoder
import io.circe.parser.decode
import journal.Logger

import scala.concurrent.{ExecutionContext, Future}

trait EventSource[A] {

  /**
    * Creates a Source of A from the given ''iri'' ''offset'' and ''cred''
    *
    * @param iri    the address from where to obtain the source
    * @param offset the offset form where to start streaming the source
    * @param cred   the optional credentials
    */
  def apply(iri: AbsoluteIri, offset: Option[String] = None)(implicit cred: Option[AuthToken]): Source[A, NotUsed]

}

object EventSource {

  /**
    * Creates an event Source using SSE and the alpakka client
    *
    * @param config the client configuration
    * @tparam A the type of the data parameter on the SSE, attempted to convert using Json
    */
  def apply[A: Decoder](
      config: IamClientConfig)(implicit as: ActorSystem, mt: Materializer, ec: ExecutionContext): EventSource[A] =
    new EventSource[A] {
      private val logger = Logger[this.type]
      private val http   = Http()

      private def addCredentials(request: HttpRequest)(implicit cred: Option[AuthToken]): HttpRequest =
        cred.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)

      private def send(request: HttpRequest)(implicit cred: Option[AuthToken]): Future[HttpResponse] =
        http.singleRequest(addCredentials(request)).map { resp =>
          if (!resp.status.isSuccess())
            logger.warn(s"HTTP response when performing SSE request: status = '${resp.status}'")
          resp
        }

      override def apply(iri: AbsoluteIri, offset: Option[String])(
          implicit cred: Option[AuthToken]): Source[A, NotUsed] =
        SSESource(iri.toAkkaUri, send, offset, config.sseRetryDelay).flatMapConcat { sse =>
          decode[A](sse.data) match {
            case Right(ev) => Source.single(ev)
            case Left(err) =>
              logger.error(s"Failed to decode admin event '$sse'", err)
              Source.empty
          }
        }
    }
}
