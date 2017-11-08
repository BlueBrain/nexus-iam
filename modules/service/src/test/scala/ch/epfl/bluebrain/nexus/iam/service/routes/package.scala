package ch.epfl.bluebrain.nexus.iam.service

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.service.auth.DownstreamAuthClient

import scala.concurrent.Future

package object routes {
  implicit class DownstreamAuthClientsSyntax(clients: Seq[DownstreamAuthClient[Future]]) {
    def findByRealm(realm: String): Option[DownstreamAuthClient[Future]] = Some(clients(0))
    def findByIssuer(issuer: Uri): Option[DownstreamAuthClient[Future]]  = Some(clients(0))
  }
}
