package ch.epfl.bluebrain.nexus.iam.routes

import akka.http.scaladsl.server.Directives.{pathPrefix, reject}
import akka.http.scaladsl.server.Route
import ch.epfl.bluebrain.nexus.iam.acls.Acls
import ch.epfl.bluebrain.nexus.iam.config.AppConfig
import com.github.ghik.silencer.silent

class AclsRoutes[F[_]](@silent acls: Acls[F])(implicit config: AppConfig) {
  def routes: Route =
    pathPrefix(config.http.prefix) {
      reject()
    }
}
