package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import cats.effect.{Effect, IO, LiftIO}
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.rdf.syntax._
import ch.epfl.bluebrain.nexus.iam.client.IamClientError.{Forbidden, Unauthorized, UnknownError, UnmarshallingError}
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.iam.client.types.events.Event
import ch.epfl.bluebrain.nexus.iam.client.types.events.Event.{AclEvent, PermissionsEvent, RealmEvent}
import ch.epfl.bluebrain.nexus.rdf.Iri.{AbsoluteIri, Path}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{DecodingFailure, Json, ParsingFailure}
import journal.Logger

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

class IamClient[F[_]] private[client] (
    source: EventSource[Event],
    config: IamClientConfig,
    aclsClient: HttpClient[F, AccessControlLists],
    callerClient: HttpClient[F, Caller],
    permissionsClient: HttpClient[F, Permissions],
    jsonClient: HttpClient[F, Json]
)(implicit F: Effect[F], mt: Materializer) {

  private val log = Logger[this.type]

  /**
    * Retrieve the current ''acls'' for some particular ''path''.
    *
    * @param path        the target resource
    * @param ancestors   matches only the exact ''path'' (false) or its ancestors also (true)
    * @param self        matches only the caller identities
    * @param credentials an optionally available token
    */
  def acls(path: Path, ancestors: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[AuthToken]): F[AccessControlLists] = {
    val endpoint = config.aclsIri + path
    val req      = requestFrom(endpoint, Query("ancestors" -> ancestors.toString, "self" -> self.toString))
    aclsClient(req).handleErrorWith(handleError(req, "acls fetch"))
  }

  /**
    * Retrieve the identities on a [[Caller]] object from the implicitly optional [[AuthToken]]
    *
    */
  def identities(implicit credentials: Option[AuthToken]): F[Caller] = {
    credentials
      .map { _ =>
        val req = requestFrom(config.identitiesIri)
        callerClient(req).handleErrorWith(handleError(req, "identities fetch"))
      }
      .getOrElse(F.pure(Caller.anonymous))
  }

  /**
    * Fetch available permissions.
    *
    * @param credentials an optionally available token
    * @return available permissions
    */
  def permissions(implicit credentials: Option[AuthToken]): F[Set[Permission]] = {
    val req = requestFrom(config.permissionsIri)
    permissionsClient(req).map(_.permissions).handleErrorWith(handleError(req, "permissions fetch"))
  }

  /**
    * Replace ACL at a given path.
    *
    * @param path       [[Path]] for which to replace the ACL
    * @param acl        updated [[AccessControlList]]
    * @param rev        current revision
    * @param credentials an optionally available token
    */
  def putAcls(path: Path, acl: AccessControlList, rev: Option[Long] = None)(
      implicit credentials: Option[AuthToken]): F[Unit] = {
    implicit val _ = config
    val endpoint   = config.aclsIri + path
    val entity     = HttpEntity(ContentTypes.`application/json`, acl.asJson.noSpaces)
    val query      = rev.map(r => Query("rev" -> r.toString)).getOrElse(Query.Empty)
    val req        = Put(endpoint.toAkkaUri.withQuery(query), entity)
    val reqWithCredentials =
      credentials.map(token => req.addCredentials(OAuth2BearerToken(token.value))).getOrElse(req)
    jsonClient(reqWithCredentials).handleErrorWith(handleError(req, "acls replace")) *> F.unit
  }

  /**
    * Checks the presence of a specific ''permission'' on a particular ''path''.
    *
    * @param path        the target resource
    * @param permission  the permission to check
    * @param credentials an optionally available token
    */
  def hasPermission(path: Path, permission: Permission)(implicit credentials: Option[AuthToken]): F[Boolean] =
    acls(path, ancestors = true, self = true).flatMap { acls =>
      val found = acls.value.exists { case (_, acl) => acl.value.permissions.contains(permission) }
      if (found) F.pure(true)
      else F.pure(false)
    }

  /**
    * It applies the provided function ''f'' to the ACLs Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[AclEvent]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def aclEvents(f: AclEvent => F[Unit], offset: Option[String] = None)(implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: AclEvent => f(ev) }
    events(config.aclsIri + "events", pf, offset)
  }

  /**
    * It applies the provided function ''f'' to the Permissions Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[PermissionsEvent]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def permissionEvents(f: PermissionsEvent => F[Unit], offset: Option[String] = None)(
      implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: PermissionsEvent => f(ev) }
    events(config.permissionsIri + "events", pf, offset)
  }

  /**
    * It applies the provided function ''f'' to the Realms Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[RealmEvent]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def realmEvents(f: RealmEvent => F[Unit], offset: Option[String] = None)(implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: RealmEvent => f(ev) }
    events(config.realmsIri + "events", pf, offset)
  }

  /**
    * It applies the provided function ''f'' to the Server-sent events (SSE)
    *
    * @param f      the function that gets executed when a new [[Event]] appears
    * @param offset the optional offset from where to start streaming the events
    */
  def events(f: Event => F[Unit], offset: Option[String] = None)(implicit cred: Option[AuthToken]): Unit = {
    val pf: PartialFunction[Event, F[Unit]] = { case ev: Event => f(ev) }
    events(config.internalIri + "events", pf, offset)
  }

  private def events(iri: AbsoluteIri, f: PartialFunction[Event, F[Unit]], offset: Option[String])(
      implicit cred: Option[AuthToken]): Unit =
    source(iri, offset)
      .mapAsync(1) { event =>
        f.lift(event) match {
          case Some(evaluated) => F.toIO(evaluated).unsafeToFuture()
          case _               => Future.unit
        }
      }
      .to(Sink.ignore)
      .mapMaterializedValue(_ => ())
      .run()

  private def handleError[A](req: HttpRequest, intent: String): Throwable => F[A] = {
    case UnexpectedUnsuccessfulHttpResponse(response, body) =>
      F.raiseError(UnknownError(response.status, body))
    case NonFatal(th) =>
      log.error(s"Unexpected response for IAM '$intent' call. Request: '${req.method} ${req.uri}'", th)
      F.raiseError(UnknownError(StatusCodes.InternalServerError, th.getMessage))
  }

  private def requestFrom(iri: AbsoluteIri, query: Query = Query.Empty)(implicit credentials: Option[AuthToken]) = {
    val request = Get(iri.toAkkaUri.withQuery(query))
    credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
  }

}

// $COVERAGE-OFF$
object IamClient {

  private def httpClient[F[_], A: ClassTag](
      implicit L: LiftIO[F],
      F: Effect[F],
      ec: ExecutionContext,
      mt: Materializer,
      cl: UntypedHttpClient[F],
      um: FromEntityUnmarshaller[A]
  ): HttpClient[F, A] = new HttpClient[F, A] {
    private val logger = Logger(s"IamHttpClient[${implicitly[ClassTag[A]]}]")

    override def apply(req: HttpRequest): F[A] =
      cl.apply(req).flatMap { resp =>
        resp.status match {
          case StatusCodes.Unauthorized =>
            cl.toString(resp.entity).flatMap { entityAsString =>
              F.raiseError[A](Unauthorized(entityAsString))
            }
          case StatusCodes.Forbidden =>
            logger.error(s"Received Forbidden when accessing '${req.method.name()} ${req.uri.toString()}'.")
            cl.toString(resp.entity).flatMap { entityAsString =>
              F.raiseError[A](Forbidden(entityAsString))
            }
          case other if other.isSuccess() =>
            val value = L.liftIO(IO.fromFuture(IO(um(resp.entity))))
            value.recoverWith {
              case pf: ParsingFailure =>
                logger.error(
                  s"Failed to parse a successful response of '${req.method.name()} ${req.getUri().toString}'.")
                F.raiseError[A](UnmarshallingError(pf.getMessage()))
              case df: DecodingFailure =>
                logger.error(
                  s"Failed to decode a successful response of '${req.method.name()} ${req.getUri().toString}'.")
                F.raiseError(UnmarshallingError(df.getMessage()))
            }
          case other =>
            cl.toString(resp.entity).flatMap { entityAsString =>
              logger.error(
                s"Received '${other.value}' when accessing '${req.method.name()} ${req.uri.toString()}', response entity as string: '$entityAsString.'")
              F.raiseError[A](UnknownError(other, entityAsString))
            }
        }
      }

    override def discardBytes(entity: HttpEntity): F[HttpMessage.DiscardedEntity] =
      cl.discardBytes(entity)

    override def toString(entity: HttpEntity): F[String] =
      cl.toString(entity)
  }

  /**
    * Constructs an ''IamClient[F]'' from implicitly available instances of [[IamClientConfig]], [[ActorSystem]] and [[Effect]].
    *
    * @tparam F the effect type
    * @return a new [[IamClient]]
    */
  final def apply[F[_]: Effect](implicit config: IamClientConfig, as: ActorSystem): IamClient[F] = {
    implicit val mt: ActorMaterializer        = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = as.dispatcher
    implicit val ucl: UntypedHttpClient[F]    = HttpClient.untyped[F]

    val aclsClient: HttpClient[F, AccessControlLists] = httpClient[F, AccessControlLists]
    val callerClient: HttpClient[F, Caller]           = httpClient[F, Caller]
    val permissionsClient: HttpClient[F, Permissions] = httpClient[F, Permissions]
    val jsonClient: HttpClient[F, Json]               = httpClient[F, Json]
    val sse: EventSource[Event]                       = EventSource[Event](config)
    new IamClient(sse, config, aclsClient, callerClient, permissionsClient, jsonClient)
  }
}
// $COVERAGE-ON$
