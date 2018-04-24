package ch.epfl.bluebrain.nexus.iam.client

import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.commons.types.identity.User
import ch.epfl.bluebrain.nexus.iam.client.Caller.{AnonymousCaller, AuthenticatedCaller}
import ch.epfl.bluebrain.nexus.iam.client.types.FullAccessControlList
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._
import journal.Logger
import ch.epfl.bluebrain.nexus.service.http.UriOps._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Iam client contract.
  *
  * @tparam F the monadic effect type
  */
trait IamClient[F[_]] {

  /**
    * Retrieve the ''caller'' from the implicitly optional [[OAuth2BearerToken]]
    *
    * @param filterGroups   if true, will only return groups currently in use in IAM
    *
    */
  def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[OAuth2BearerToken]): F[Caller]

  /**
    * Retrieve the current ''acls'' for some particular ''resource''
    *
    * @param resource the resource against which to check the acls
    * @param parents  decides whether it should match only the provided ''path'' (false)
    *                 or the parents also (true)
    * @param self     decides whether it should match only the provided ''identities'' (true)
    *                 or any identity which has the right own access (true)    * @param credentials    a possibly available token
    */
  def getAcls(resource: Path, parents: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[OAuth2BearerToken]): F[FullAccessControlList]
}

object IamClient {
  private val log               = Logger[this.type]
  private val Acls              = Path("acls")
  private val User              = "oauth2" / "user"
  private val filterGroupsParam = "filterGroups"

  final def apply()(implicit ec: ExecutionContext,
                    aclClient: HttpClient[Future, FullAccessControlList],
                    userClient: HttpClient[Future, User],
                    iamUri: IamUri): IamClient[Future] = new IamClient[Future] {

    override def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[OAuth2BearerToken]) =
      credentials
        .map { cred =>
          userClient(requestFrom(User, Query(filterGroupsParam -> filterGroups.toString)))
            .map[Caller](AuthenticatedCaller(cred, _))
            .recoverWith[Caller] { case e => recover(e, User) }
        }
        .getOrElse(Future.successful(AnonymousCaller()))

    override def getAcls(resource: Path, parents: Boolean = false, self: Boolean = false)(
        implicit credentials: Option[OAuth2BearerToken]) = {
      aclClient(requestFrom(Acls ++ resource, Query("parents" -> parents.toString, "self" -> self.toString)))
        .recoverWith[FullAccessControlList] { case e => recover(e, resource) }
    }

    def recover(th: Throwable, resource: Path) = th match {
      case UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.Unauthorized, _, _, _)) =>
        Future.failed(UnauthorizedAccess)
      case ur: UnexpectedUnsuccessfulHttpResponse =>
        log.warn(
          s"Received an unexpected response status code '${ur.response.status}' from IAM when attempting to perform and operation on a resource '$resource'")
        Future.failed(ur)
      case err =>
        log.error(
          s"Received an unexpected exception from IAM when attempting to perform and operation on a resource '$resource'",
          err)
        Future.failed(err)
    }

    private def requestFrom(path: Path, query: Query)(implicit credentials: Option[OAuth2BearerToken]) = {
      val request = Get(iamUri.value.append(path).withQuery(query))
      credentials.map(request.addCredentials).getOrElse(request)
    }
  }
}
