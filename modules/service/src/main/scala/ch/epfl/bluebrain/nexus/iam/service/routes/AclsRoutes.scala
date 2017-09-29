package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.iam.core.acls.Acls
import ch.epfl.bluebrain.nexus.iam.core.acls.Permissions._
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._
import ch.epfl.bluebrain.nexus.iam.service.types.{AccessControl, AccessControlList}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.directives.AclDirectives._
import io.circe.generic.auto._

import scala.concurrent.Future

/**
  * HTTP routes for ACL specific functionality.
  *
  * @param acl the ACL operations bundle
  */
class AclsRoutes(acl: Acls[Future]) extends DefaultRoutes("acls") {

  override def apiRoutes: Route = {
    implicit val caller: Identity = Anonymous

    extractExecutionContext { implicit ec =>
      extractResourcePath { path =>
        put {
          entity(as[AccessControlList]) { list =>
            onSuccess(acl.create(path, list.toMap)) { _ =>
              complete(StatusCodes.NoContent)
            }
          }
        } ~
          post {
            entity(as[AccessControl]) { ac =>
              onSuccess(acl.add(path, ac.identity, ac.permissions)) { _ =>
                complete(StatusCodes.NoContent)
              }
            }
          } ~
          delete {
            onSuccess(acl.clear(path)) {
              complete(StatusCodes.NoContent)
            }
          } ~
          get {
            parameters('all.as[Boolean].?) {
              case Some(true) =>
                onSuccess(acl.fetch(path)) { result =>
                  complete(StatusCodes.OK -> AccessControlList(result.toList: _*))
                }
              case _ =>
                onSuccess(acl.fetch(path, caller)) { result =>
                  complete(StatusCodes.OK -> result)
                }
            }
          }
      }
    }
  }
}
