package ch.epfl.bluebrain.nexus.iam.routes

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, Rejection => AkkaRejection}
import ch.epfl.bluebrain.nexus.iam.acls.{AccessControlList, AccessControlLists, Acls, ResourceAccessControlList}
import ch.epfl.bluebrain.nexus.iam.config.AppConfig
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.tracing._
import ch.epfl.bluebrain.nexus.iam.directives.AclDirectives._
import ch.epfl.bluebrain.nexus.iam.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.routes.AclsRoutes.PatchAcl
import ch.epfl.bluebrain.nexus.iam.routes.AclsRoutes.PatchAcl.{AppendAcl, SubtractAcl}
import ch.epfl.bluebrain.nexus.iam.types.Caller
import ch.epfl.bluebrain.nexus.service.http.Path
import com.github.ghik.silencer.silent
import io.circe.{Decoder, DecodingFailure}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import ch.epfl.bluebrain.nexus.iam.routes.AclsRoutes._

class AclsRoutes(acls: Acls[Task], realms: Realms[Task])(implicit @silent config: AppConfig) {

  private val simultaneousParamsRejection: AkkaRejection =
    validationRejection("'rev' and 'ancestors' query parameters cannot be present simultaneously.")

  def routes: Route =
    (handleRejections(RejectionHandling()) & handleExceptions(ExceptionHandling())) {
      pathPrefix(config.http.prefix / "acls") {
        authenticateOAuth2Async("*", authenticator(realms)).withAnonymousUser(Caller.anonymous) { implicit caller =>
          extractResourcePath { path =>
            parameter("rev" ? 0L) { rev =>
              (put & entity(as[AccessControlList])) { acl =>
                trace("replace ACL") {
                  complete(Created -> acls.replace(path, rev, acl).runToFuture)
                }
              } ~
                (patch & entity(as[PatchAcl])) {
                  case AppendAcl(acl) =>
                    trace("append ACL") {
                      complete(acls.append(path, rev, acl).runToFuture)
                    }
                  case SubtractAcl(acl) =>
                    trace("subtract ACL") {
                      complete(acls.subtract(path, rev, acl).runToFuture)
                    }
                } ~
                delete {
                  trace("delete ACL") {
                    complete(acls.delete(path, rev).runToFuture)
                  }
                }
            } ~
              (get & parameter("rev".as[Long] ?) & parameter("ancestors" ? false) & parameter("self" ? true)) {
                case (Some(_), true, _) =>
                  reject(simultaneousParamsRejection)
                case (Some(rev), false, true) =>
                  complete(acls.fetch(path, rev).toSingleList(path).runToFuture)
                //TODO: Instead of fetchUnsafe, make sure the fetch checks if you have permissions to see all the acls
                case (Some(rev), false, false) =>
                  complete(acls.fetchUnsafe(path, rev).toSingleList(path).runToFuture)
                case (_, true, self) =>
                  complete(acls.list(path, ancestors = true, self).runToFuture)
                case (_, _, false) =>
                  complete(acls.fetchUnsafe(path).toSingleList(path).runToFuture)
                case (_, _, true) =>
                  complete(acls.fetch(path).toSingleList(path).runToFuture)
              }
          }
        }
      }
    }

}

object AclsRoutes {

  private[routes] implicit class TaskResourceACLSyntax(private val value: Task[ResourceAccessControlList])
      extends AnyVal {
    def toSingleList(path: Path): Task[AccessControlLists] = value.map(acl => AccessControlLists(path -> acl))
  }

  private[routes] sealed trait PatchAcl

  private[routes] object PatchAcl {

    final case class SubtractAcl(acl: AccessControlList) extends PatchAcl
    final case class AppendAcl(acl: AccessControlList)   extends PatchAcl

    implicit val patchAclDecoder: Decoder[PatchAcl] =
      Decoder.instance { hc =>
        for {
          tpe <- hc.get[String]("@type")
          acl <- hc.value.as[AccessControlList]
          patch <- tpe match {
            case "Append"   => Right(AppendAcl(acl))
            case "Subtract" => Right(SubtractAcl(acl))
            case _          => Left(DecodingFailure("@type field must have Append or Subtract value", hc.history))
          }
        } yield patch
      }
  }
}
