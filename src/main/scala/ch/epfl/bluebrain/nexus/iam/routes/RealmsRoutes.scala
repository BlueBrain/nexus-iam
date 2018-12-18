package ch.epfl.bluebrain.nexus.iam.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.tracing._
import ch.epfl.bluebrain.nexus.iam.config.Contexts.{iamCtxUri, resourceCtxUri, searchCtxUri}
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.iam.directives.AuthDirectives.authenticator
import ch.epfl.bluebrain.nexus.iam.directives.RealmDirectives._
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.realms.{Realms, Resource}
import ch.epfl.bluebrain.nexus.iam.routes.RealmsRoutes.{NewRealm, UpdateRealm}
import ch.epfl.bluebrain.nexus.iam.types.Caller
import ch.epfl.bluebrain.nexus.iam.types.ResourceF.resourceMetaEncoder
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

/**
  * The realms routes.
  *
  * @param realms the realms api
  */
class RealmsRoutes(realms: Realms[Task])(implicit http: HttpConfig) {

  private implicit val resourceEncoder: Encoder[Resource] =
    Encoder.encodeJson.contramap { r =>
      resourceMetaEncoder.apply(r.discard) deepMerge r.value.fold(_.asJson, _.asJson)
    }

  private implicit val resourceListEncoder: Encoder[List[Resource]] =
    Encoder.encodeJson.contramap[List[Resource]] { l =>
      Json
        .obj(nxv.total.prefix   -> Json.fromInt(l.size),
             nxv.results.prefix -> Json.arr(l.map(r => resourceEncoder(r).removeKeys("@context")): _*))
        .addContext(resourceCtxUri)
        .addContext(iamCtxUri)
        .addContext(searchCtxUri)
    }

  def routes: Route =
    (handleRejections(RejectionHandling()) & handleExceptions(ExceptionHandling())) {
      pathPrefix(http.prefix / "realms") {
        authenticateOAuth2Async("*", authenticator(realms)).withAnonymousUser(Caller.anonymous) { implicit caller =>
          concat(
            (get & pathEndOrSingleSlash) {
              trace("listRealms") {
                complete(realms.list.runToFuture)
              }
            },
            (put & label & pathEndOrSingleSlash) { id =>
              parameter("rev".as[Long].?) {
                case Some(rev) =>
                  entity(as[UpdateRealm]) {
                    case UpdateRealm(name, openIdConfig, logo) =>
                      trace("updateRealm") {
                        complete(realms.update(id, rev, name, openIdConfig, logo).runToFuture)
                      }
                  }
                case None =>
                  entity(as[NewRealm]) {
                    case NewRealm(name, openIdConfig, logo) =>
                      trace("createRealm") {
                        complete(StatusCodes.Created -> realms.create(id, name, openIdConfig, logo).runToFuture)
                      }
                  }
              }
            },
            (get & label & pathEndOrSingleSlash) { id =>
              parameter("rev".as[Long].?) {
                case Some(rev) =>
                  trace("getRealmByIdAndRev") {
                    complete(realms.fetch(id, rev).runToFuture)
                  }
                case None =>
                  trace("getRealmById") {
                    complete(realms.fetch(id).runToFuture)
                  }
              }
            },
            (delete & label & pathEndOrSingleSlash) { id =>
              parameter("rev".as[Long]) { rev =>
                trace("deprecateRealm") {
                  complete(realms.deprecate(id, rev).runToFuture)
                }
              }
            }
          )
        }
      }
    }
}

object RealmsRoutes {

  private[routes] final case class NewRealm(name: String, openIdConfig: Url, logo: Option[Url])
  private[routes] object NewRealm {
    implicit val newRealmDecoder: Decoder[NewRealm] = deriveDecoder[NewRealm]
  }
  private[routes] final case class UpdateRealm(name: Option[String], openIdConfig: Option[Url], logo: Option[Url])
  private[routes] object UpdateRealm {
    implicit val updateRealmDecoder: Decoder[UpdateRealm] = deriveDecoder[UpdateRealm]
  }

}
