package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
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
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}

sealed trait IamClient[F[_]] {

  /**
    * Retrieve the ''caller'' from the implicitly optional [[AuthToken]]
    *
    * @param filterGroups   if true, will only return groups currently in use in IAM
    *
    */
  def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[AuthToken]): F[Caller]

  /**
    * Retrieve the current ''acls'' for some particular ''path''.
    *
    * @param path        the resource against which to check the acls
    * @param ancestors   matches only the exact ''path'' (false) or its ancestors also (true)
    * @param self        matches only the caller identities
    * @param credentials an optionally available token
    */
  def getAcls(path: Path, ancestors: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[AuthToken]): F[AccessControlLists]

}

object IamClient {

  private val log             = Logger[this.type]
  private val filterGroupsKey = "filterGroups"

  final case object UserRefNotFound extends Err("Missing UserRef")

  /**
    * Constructs an ''IamClient[Future]''
    *
    * @return a new [[IamClient]] of [[Future]] context
    */
  // $COVERAGE-OFF$
  final def apply()(implicit config: IamClientConfig, as: ActorSystem): IamClient[Future] = {
    implicit val mt           = ActorMaterializer()
    implicit val ec           = as.dispatcher
    implicit val ucl          = akkaHttpClient
    implicit val aclsClient   = withAkkaUnmarshaller[AccessControlLists]
    implicit val callerClient = withAkkaUnmarshaller[Caller]
    fromFuture
  }
  // $COVERAGE-ON$

  private[client] final def fromFuture(implicit ec: ExecutionContext,
                                       aclsClient: HttpClient[Future, AccessControlLists],
                                       callerClient: HttpClient[Future, Caller],
                                       config: IamClientConfig): IamClient[Future] = new IamClient[Future] {

    def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[AuthToken]): Future[Caller] =
      credentials
        .map { _ =>
          callerClient(requestFrom(config.prefix / "oauth2" / "user", Query(filterGroupsKey -> filterGroups.toString)))
            .recoverWith[Caller] { case e => recover(e, config.prefix / "oauth2" / "user") }
        }
        .getOrElse(Future.successful(Caller.anonymous))

    def getAcls(path: Path, ancestors: Boolean = false, self: Boolean = false)(
        implicit credentials: Option[AuthToken]): Future[AccessControlLists] = {
      val req =
        requestFrom(path :: (config.prefix / "acls"), Query("ancestors" -> ancestors.toString, "self" -> self.toString))
      aclsClient(req).recoverWith[AccessControlLists] { case e => recover(e, path) }
    }

    private def recover(th: Throwable, path: Path) = th match {
      case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
        Future.failed(UnauthorizedAccess)
      case ur: UnexpectedUnsuccessfulHttpResponse =>
        log.warn(
          s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to perform and operation on a resource '$path'")
        Future.failed(ur)
      case err =>
        log.error(
          s"Received an unexpected exception from IAM when attempting to perform and operation on a resource '$path'",
          err)
        Future.failed(err)
    }

    private def requestFrom(path: Path, query: Query)(implicit credentials: Option[AuthToken]) = {
      val request = Get((config.publicIri + path).toAkkaUri.withQuery(query))
      credentials.map(request.addCredentials(_)).getOrElse(request)
    }
  }

  /**
    * Constructs an ''IamClient[Task]''
    *
    * @return a new [[IamClient]] of [[Task]] context
    */
  // $COVERAGE-OFF$
  final def task()(implicit config: IamClientConfig, as: ActorSystem): IamClient[Task] = {
    implicit val mt                             = ActorMaterializer()
    implicit val ec                             = as.dispatcher
    implicit val ucl: UntypedHttpClient[Future] = akkaHttpClient
    implicit val aclsClient                     = withAkkaUnmarshaller[AccessControlLists]
    implicit val callerClient                   = withAkkaUnmarshaller[Caller]
    val underlying                              = fromFuture
    new IamClient[Task] {

      override def getCaller(filterGroups: Boolean)(implicit credentials: Option[AuthToken]) =
        Task.deferFuture(underlying.getCaller(filterGroups))

      override def getAcls(path: Path, ancestors: Boolean = false, self: Boolean = false)(
          implicit credentials: Option[AuthToken]) = Task.deferFuture(underlying.getAcls(path, ancestors, self))
    }
  }
  // $COVERAGE-ON$

  private implicit def toAkka(token: AuthToken): OAuth2BearerToken = OAuth2BearerToken(token.value)

}
