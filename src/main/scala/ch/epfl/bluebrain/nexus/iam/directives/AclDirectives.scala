package ch.epfl.bluebrain.nexus.iam.directives

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._

trait AclDirectives {

  /**
    * Extracts the [[Path]] from the unmatched segments
    */
  def extractResourcePath: Directive1[Path] = extractUnmatchedPath.map(toInternal)
}

object AclDirectives extends AclDirectives
