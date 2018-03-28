package ch.epfl.bluebrain.nexus.iam.service.directives

import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._

trait AclDirectives {

  def extractResourcePath: Directive1[Path] = extractUnmatchedPath.map(toInternal)

}

object AclDirectives extends AclDirectives
