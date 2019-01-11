package ch.epfl.bluebrain.nexus.iam.routes

import akka.http.javadsl.server.Rejections.validationRejection
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, Rejection => AkkaRejection}
import ch.epfl.bluebrain.nexus.iam.acls._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.tracing._
import ch.epfl.bluebrain.nexus.iam.directives.AclDirectives._
import ch.epfl.bluebrain.nexus.iam.directives.AuthDirectives._
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.routes.AclsRoutes.PatchAcl.{AppendAcl, SubtractAcl}
import ch.epfl.bluebrain.nexus.iam.routes.AclsRoutes._
import ch.epfl.bluebrain.nexus.iam.types.Caller
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import io.circe.{Decoder, DecodingFailure}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

class AclsRoutes(acls: Acls[Task], realms: Realms[Task])(implicit http: HttpConfig) {

  private val any = "*"

  private val simultaneousRevAndAncestorsRejection: AkkaRejection =
    validationRejection("'rev' and 'ancestors' query parameters cannot be present simultaneously.")

  private val simultaneousRevAndAnyRejection: AkkaRejection =
    validationRejection("'rev' query parameter and path containing '*' cannot be present simultaneously.")

  def routes: Route =
    (handleRejections(RejectionHandling()) & handleExceptions(ExceptionHandling())) {
      pathPrefix(http.prefix / "acls") {
        authenticateOAuth2Async("*", authenticator(realms)).withAnonymousUser(Caller.anonymous) { implicit caller =>
          extractResourcePath { path =>
            concat(
              parameter("rev" ? 0L) { rev =>
                concat(
                  (put & entity(as[AccessControlList])) { acl =>
                    trace("replaceAcl") {
                      complete(Created -> acls.replace(path, rev, acl).runToFuture)
                    }
                  },
                  (patch & entity(as[PatchAcl])) {
                    case AppendAcl(acl) =>
                      trace("appendAcl") {
                        complete(acls.append(path, rev, acl).runToFuture)
                      }
                    case SubtractAcl(acl) =>
                      trace("subtractAcl") {
                        complete(acls.subtract(path, rev, acl).runToFuture)
                      }
                  },
                  delete {
                    trace("deleteAcl") {
                      complete(acls.delete(path, rev).runToFuture)
                    }
                  }
                )
              },
              (get & parameter("rev".as[Long] ?) & parameter("ancestors" ? false) & parameter("self" ? true)) {
                case (Some(_), true, _) =>
                  reject(simultaneousRevAndAncestorsRejection)
                case (Some(_), _, _) if path.segments.contains(any) =>
                  reject(simultaneousRevAndAnyRejection)
                case (_, ancestors, self) if path.segments.contains(any) =>
                  trace("listAcls") {
                    complete(acls.list(path, ancestors, self).runToFuture)
                  }
                case (Some(rev), false, self) =>
                  trace("fetchAcl") {
                    complete(acls.fetch(path, rev, self).toSingleList(path).runToFuture)
                  }
                case (_, false, self) =>
                  trace("fetchAcl") {
                    complete(acls.fetch(path, self).toSingleList(path).runToFuture)
                  }
                case (_, true, self) =>
                  trace("listAcls") {
                    complete(acls.list(path, ancestors = true, self).runToFuture)
                  }
              }
            )
          }
        }
      }
    }
}

object AclsRoutes {

  private[routes] implicit class TaskResourceACLSyntax(private val value: Task[ResourceOpt]) extends AnyVal {
    def toSingleList(path: Path): Task[AccessControlLists] = value.map {
      case None                                              => AccessControlLists.empty
      case Some(acl) if acl.value == AccessControlList.empty => AccessControlLists.empty
      case Some(acl)                                         => AccessControlLists(path -> acl)
    }
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
