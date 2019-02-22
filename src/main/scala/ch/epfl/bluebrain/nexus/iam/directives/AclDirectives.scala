package ch.epfl.bluebrain.nexus.iam.directives

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.commons.rdf.syntax._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path

trait AclDirectives {

  /**
    * Extracts the [[Path]] from the unmatched segments
    */
  def extractResourcePath: Directive1[Path] = extractUnmatchedPath.flatMap { path =>
    path.toIriPath match {
      case p if p.asString.contains("//") =>
        reject(validationRejection(s"Path '${p.asString}' cannot contain double slash."))
      case p if p.isEmpty => provide(Path./)
      case p              => provide(p)
    }
  }
}

object AclDirectives extends AclDirectives
