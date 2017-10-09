package ch.epfl.bluebrain.nexus.iam.service.directives

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.iam.core.acls.Path

import scala.annotation.tailrec

trait AclDirectives {

  def extractResourcePath: Directive1[Path] = extractUnmatchedPath.map(transform)

  /**
    * Maps path segments from akka's representation to the internal application type.
    *
    * @param from akka's path representation
    * @return the internal path representation
    */
  private def transform(from: Uri.Path): Path = {
    @tailrec
    def inner(acc: Path, remaining: Uri.Path): Path = remaining match {
      case Uri.Path.SingleSlash         => acc
      case Uri.Path.Empty               => acc
      case Uri.Path.Slash(tail)         => inner(acc, tail)
      case Uri.Path.Segment(head, tail) => inner(acc / head, tail)
    }

    inner(Path./, from)
  }

}

object AclDirectives extends AclDirectives