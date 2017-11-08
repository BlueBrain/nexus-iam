package ch.epfl.bluebrain.nexus.iam.service

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives.{Neutral, separateOnSlashes}
import akka.http.scaladsl.server.PathMatcher0
import ch.epfl.bluebrain.nexus.iam.service.auth.DownstreamAuthClient

import scala.concurrent.Future

package object routes {

  private def stripLeadingSlash(path: Path): Path =
    if (path.startsWithSlash) path.tail
    else path

  final def prefixOf(publicUri: Uri): PathMatcher0 = {
    val path = publicUri.path
    if (path.isEmpty || path == Path.SingleSlash) Neutral
    else separateOnSlashes(stripLeadingSlash(path).toString())
  }

  final def prefixOf(publicUri: Uri, prefix: String): PathMatcher0 = {
    val path = publicUri.path
    if (path.isEmpty || path == Path.SingleSlash) separateOnSlashes(prefix)
    else prefixOf(publicUri) / prefix
  }

  /**
    * Expose method on [[Seq[DownstreamAuthClient[Future]] object
    *
    * @param clients the [Seq[DownstreamAuthClient[Future where API methods are going to be exposed
    */
  implicit class DownstreamAuthClientsSyntax(clients: Seq[DownstreamAuthClient[Future]]) {

    /**
      * Expose a method to fetch the [[DownstreamAuthClient[Future]] from the provided ''realm''
      *
      * @param realm the realm
      */
    def findByRealm(realm: String): Option[DownstreamAuthClient[Future]] = clients.find(_.config.realm == realm)

    /**
      * Expose a method to fetch the [[DownstreamAuthClient[Future]] from the provided ''issuer''
      *
      * @param issuer the token issuer
      */
    def findByIssuer(issuer: Uri): Option[DownstreamAuthClient[Future]] = clients.find(_.config.issuer == issuer)
  }
}
