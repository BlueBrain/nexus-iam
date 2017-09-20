package ch.epfl.bluebrain.nexus.iam.service

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.{Path => AkkaHttpPath}
import akka.http.scaladsl.server.Directives.{Neutral, separateOnSlashes}
import akka.http.scaladsl.server.PathMatcher0
import ch.epfl.bluebrain.nexus.iam.core.acls.Path

import scala.annotation.tailrec

package object routes {

  /**
    * Maps path segments from akka's representation to the internal application type.
    *
    * @param from akka's path representation
    * @return the internal path representation
    */
  private[routes] def transform(from: Uri.Path): Path = {
    @tailrec
    def inner(acc: Path, remaining: Uri.Path): Path = remaining match {
      case Uri.Path.SingleSlash         => acc
      case Uri.Path.Empty               => acc
      case Uri.Path.Slash(tail)         => inner(acc, tail)
      case Uri.Path.Segment(head, tail) => inner(acc / head, tail)
    }

    inner(Path./, from)
  }

  private def stripLeadingSlash(path: AkkaHttpPath): AkkaHttpPath =
    if (path.startsWithSlash) path.tail
    else path

  final def prefixOf(publicUri: Uri): PathMatcher0 = {
    val path = publicUri.path
    if (path.isEmpty || path == AkkaHttpPath.SingleSlash) Neutral
    else separateOnSlashes(stripLeadingSlash(path).toString())
  }

  final def prefixOf(publicUri: Uri, prefix: String): PathMatcher0 = {
    val path = publicUri.path
    if (path.isEmpty || path == AkkaHttpPath.SingleSlash) separateOnSlashes(prefix)
    else prefixOf(publicUri) / prefix
  }
}
