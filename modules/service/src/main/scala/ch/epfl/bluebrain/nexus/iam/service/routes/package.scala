package ch.epfl.bluebrain.nexus.iam.service

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server.Directives.{Neutral, separateOnSlashes}
import akka.http.scaladsl.server.PathMatcher0

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
}
