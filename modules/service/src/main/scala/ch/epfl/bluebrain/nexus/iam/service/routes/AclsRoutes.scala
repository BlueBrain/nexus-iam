package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.acls.Permissions._
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._
import ch.epfl.bluebrain.nexus.iam.service.directives.AclDirectives._
import ch.epfl.bluebrain.nexus.iam.service.routes.AclsRoutes._
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejections._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Decoder
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
            onSuccess(acl.create(path, list)) {
              complete(StatusCodes.Created)
            }
          }
        } ~
          post {
            entity(as[AccessControl]) { ac =>
              onSuccess(acl.add(path, ac.identity, ac.permissions)) { result =>
                complete(StatusCodes.OK -> AccessControl(ac.identity, result))
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
                  complete(StatusCodes.OK -> AccessControl(caller, result.getOrElse(Permissions.empty)))
                }
            }
          }
      }
    }
  }
}

object AclsRoutes {

  def apply(acl: Acls[Future]): AclsRoutes = new AclsRoutes(acl)

  implicit val decoder: Decoder[AccessControl] = Decoder.instance { cursor =>
    val fields = cursor.fields.toSeq.flatten
    if (!fields.contains("permissions"))
      throw WrongOrInvalidJson(Some("Missing field 'permissions' in payload"))
    else if (!fields.contains("identity"))
      throw WrongOrInvalidJson(Some("Missing field 'identity' in payload"))
    else
      cursor.downField("permissions").as[Permissions] match {
        case Left(df) => throw IllegalPermissionString(df.message)
        case Right(permissions) =>
          cursor.downField("identity").as[Identity] match {
            case Left(df)        => throw IllegalIdentityFormat(df.message, "identity")
            case Right(identity) => Right(AccessControl(identity, permissions))
          }
      }
  }
}
