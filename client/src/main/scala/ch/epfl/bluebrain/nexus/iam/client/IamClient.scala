package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.{ActorMaterializer, Materializer}
import cats.MonadError
import cats.effect.{IO, LiftIO}
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.iam.client.IamClientError.{Forbidden, Unauthorized, UnknownError, UnmarshallingError}
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.rdf.Iri.{AbsoluteIri, Path}
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{DecodingFailure, Json, ParsingFailure}
import journal.Logger

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.reflect.ClassTag

class IamClient[F[_]] private[client] (
    config: IamClientConfig,
    aclsClient: HttpClient[F, AccessControlLists],
    callerClient: HttpClient[F, Caller],
    permissionsClient: HttpClient[F, Permissions],
    jsonClient: HttpClient[F, Json]
)(implicit F: MonadError[F, Throwable]) {

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
    aclsClient(req)
  }

  /**
    * Retrieve the identities on a [[Caller]] object from the implicitly optional [[AuthToken]]
    *
    */
  def identities(implicit credentials: Option[AuthToken]): F[Caller] = {
    credentials
      .map(_ => callerClient(requestFrom(config.identitiesIri)))
      .getOrElse(F.pure(Caller.anonymous))
  }

  /**
    * Fetch available permissions.
    *
    * @param credentials an optionally available token
    * @return available permissions
    */
  def permissions(implicit credentials: Option[AuthToken]): F[Set[Permission]] =
    permissionsClient(requestFrom(config.permissionsIri)).map(_.permissions)

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
    val request    = Put(endpoint.toAkkaUri.withQuery(query), entity)
    val requestWithCredentials =
      credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
    jsonClient(requestWithCredentials) *> F.unit
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

  private def requestFrom(iri: AbsoluteIri, query: Query = Query.Empty)(implicit credentials: Option[AuthToken]) = {
    val request = Get(iri.toAkkaUri.withQuery(query))
    credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
  }

}

// $COVERAGE-OFF$
object IamClient {

  private def httpClient[F[_], A: ClassTag](
      implicit L: LiftIO[F],
      F: MonadError[F, Throwable],
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
    * Constructs an ''IamClient[F]'' from implicitly available instances of [[IamClientConfig]], [[ActorSystem]],
    * [[LiftIO]] and [[MonadError]].
    *
    * @tparam F the effect type
    * @return a new [[IamClient]]
    */
  final def apply[F[_]: LiftIO](implicit F: MonadError[F, Throwable],
                                config: IamClientConfig,
                                as: ActorSystem): IamClient[F] = {
    implicit val mt: ActorMaterializer        = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = as.dispatcher
    implicit val ucl: UntypedHttpClient[F]    = HttpClient.untyped[F]

    val aclsClient: HttpClient[F, AccessControlLists] = httpClient[F, AccessControlLists]
    val callerClient: HttpClient[F, Caller]           = httpClient[F, Caller]
    val permissionsClient: HttpClient[F, Permissions] = httpClient[F, Permissions]
    val jsonClient: HttpClient[F, Json]               = httpClient[F, Json]
    new IamClient(config, aclsClient, callerClient, permissionsClient, jsonClient)
  }
}
// $COVERAGE-ON$
