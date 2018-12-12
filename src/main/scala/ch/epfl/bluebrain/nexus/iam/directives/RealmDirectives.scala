package ch.epfl.bluebrain.nexus.iam.directives

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatchers.Segment
import ch.epfl.bluebrain.nexus.iam.types.Label

trait RealmDirectives {

  /**
    * Matches a path segment as a [[Label]].
    */
  def label: Directive1[Label] =
    pathPrefix(Segment).flatMap { str =>
      Label(str) match {
        case Left(err)    => reject(validationRejection(err))
        case Right(label) => provide(label)
      }
    }
}

object RealmDirectives extends RealmDirectives
