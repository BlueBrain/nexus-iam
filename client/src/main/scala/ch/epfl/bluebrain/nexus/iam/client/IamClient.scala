package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import cats.MonadError
import cats.effect.LiftIO
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.Err
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.syntax.akka._
import io.circe.generic.auto._
import journal.Logger

import scala.concurrent.ExecutionContextExecutor

class IamClient[F[_]] private[client] (config: IamClientConfig,
                                       aclsClient: HttpClient[F, AccessControlLists],
                                       callerClient: HttpClient[F, Caller])(implicit F: MonadError[F, Throwable]) {

  private val log = Logger[this.type]

  /**
    * Retrieve the ''caller'' from the implicitly optional [[AuthToken]]
    *
    */
  def getCaller(implicit credentials: Option[AuthToken]): F[Caller] =
    credentials
      .map { _ =>
        callerClient(requestFrom(config.prefix / "oauth2" / "user"))
          .recoverWith { case e => recover(e, config.prefix / "oauth2" / "user") }
      }
      .getOrElse(F.pure(Caller.anonymous))

  /**
    * Retrieve the current ''acls'' for some particular ''path''.
    *
    * @param path        the target resource
    * @param ancestors   matches only the exact ''path'' (false) or its ancestors also (true)
    * @param self        matches only the caller identities
    * @param credentials an optionally available token
    */
  def getAcls(path: Path, ancestors: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[AuthToken]): F[AccessControlLists] = {
    val req =
      requestFrom(path :: (config.prefix / "acls"), Query("ancestors" -> ancestors.toString, "self" -> self.toString))
    aclsClient(req).recoverWith { case e => recover(e, path) }
  }

  /**
    * Checks the presence of a specific ''permission'' on a particular ''path''.
    *
    * @param path        the target resource
    * @param permission  the permission to check
    * @param credentials an optionally available token
    */
  def authorizeOn(path: Path, permission: Permission)(implicit credentials: Option[AuthToken]): F[Unit] =
    getAcls(path, ancestors = true, self = true).flatMap { acls =>
      val found = acls.value.exists { case (_, acl) => acl.value.permissions.contains(permission) }
      if (found) F.unit
      else F.raiseError(UnauthorizedAccess)
    }

  private def recover[A](th: Throwable, path: Path): F[A] = th match {
    case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
      F.raiseError(UnauthorizedAccess)
    case ur: UnexpectedUnsuccessfulHttpResponse =>
      log.warn(
        s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to perform and operation on a resource '$path'")
      F.raiseError(ur)
    case err =>
      log.error(
        s"Received an unexpected exception from IAM when attempting to perform and operation on a resource '$path'",
        err)
      F.raiseError(err)
  }

  private def requestFrom(path: Path, query: Query = Query.Empty)(implicit credentials: Option[AuthToken]) = {
    val request = Get((config.publicIri + path).toAkkaUri.withQuery(query))
    credentials.map(token => request.addCredentials(OAuth2BearerToken(token.value))).getOrElse(request)
  }

}

// $COVERAGE-OFF$
object IamClient {

  final case object UserRefNotFound extends Err("Missing UserRef")

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

    val aclsClient: HttpClient[F, AccessControlLists] = HttpClient.withUnmarshaller[F, AccessControlLists]
    val callerClient: HttpClient[F, Caller]           = HttpClient.withUnmarshaller[F, Caller]
    new IamClient(config, aclsClient, callerClient)
  }
}
// $COVERAGE-ON$
