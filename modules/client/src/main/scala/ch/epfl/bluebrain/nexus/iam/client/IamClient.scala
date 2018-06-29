package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.commons.types.Err
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.Caller._
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.UserRef
import ch.epfl.bluebrain.nexus.iam.client.types.Address._
import ch.epfl.bluebrain.nexus.iam.client.types.{Address, AuthToken, FullAccessControlList, Identity}
import journal.Logger
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}

/**
  * Iam client contract.
  *
  * @tparam F the monadic effect type
  */
trait IamClient[F[_]] {

  /**
    * Retrieve the ''caller'' from the implicitly optional [[AuthToken]]
    *
    * @param filterGroups   if true, will only return groups currently in use in IAM
    *
    */
  def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[AuthToken]): F[Caller]

  /**
    * Retrieve the current ''acls'' for some particular ''resource''
    *
    * @param resource the resource against which to check the acls
    * @param parents  decides whether it should match only the provided ''path'' (false)
    *                 or the parents also (true)
    * @param self     decides whether it should match only the provided ''identities'' (true)
    *                 or any identity which has the right own access (true)    * @param credentials    a possibly available token
    */
  def getAcls(resource: Address, parents: Boolean = false, self: Boolean = false)(
      implicit credentials: Option[AuthToken]): F[FullAccessControlList]
}

object IamClient {

  private[client] final case class User(identities: Set[Identity])

  private val log             = Logger[this.type]
  private val filterGroupsKey = "filterGroups"

  final case object UserRefNotFound extends Err("Missing UserRef")

  /**
    * Constructs an ''IamClient[Future]''
    *
    * @param iamUri the iam base uri
    * @return a new [[IamClient]] of [[Future]] context
    */
  final def apply()(implicit iamUri: IamUri, as: ActorSystem): IamClient[Future] = {
    import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
    import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
    import io.circe.generic.auto._
    implicit val mt                             = ActorMaterializer()
    implicit val ec                             = as.dispatcher
    implicit val ucl: UntypedHttpClient[Future] = akkaHttpClient
    implicit val aclsClient                     = withAkkaUnmarshaller[FullAccessControlList]
    implicit val userClient                     = withAkkaUnmarshaller[User]
    fromFuture
  }

  private[client] final def fromFuture(implicit ec: ExecutionContext,
                                       aclClient: HttpClient[Future, FullAccessControlList],
                                       identitiesClient: HttpClient[Future, User],
                                       iamUri: IamUri): IamClient[Future] = new IamClient[Future] {

    def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[AuthToken]): Future[Caller] =
      credentials
        .map { cred =>
          identitiesClient(requestFrom("oauth2" / "user", Query(filterGroupsKey -> filterGroups.toString)))
            .flatMap { user =>
              user.identities.collectFirst {
                case id: UserRef => AuthenticatedCaller(cred, id, user.identities)
              } match {
                case Some(caller) => Future.successful(caller)
                case _            => Future.failed(UserRefNotFound)
              }
            }
            .recoverWith[Caller] { case e => recover(e, "oauth2" / "user") }
        }
        .getOrElse(Future.successful(AnonymousCaller))

    def getAcls(resource: Address, parents: Boolean = false, self: Boolean = false)(
        implicit credentials: Option[AuthToken]): Future[FullAccessControlList] = {
      aclClient(requestFrom(Address("acls") ++ resource, Query("parents" -> parents.toString, "self" -> self.toString)))
        .recoverWith[FullAccessControlList] { case e => recover(e, resource) }
    }

    def recover(th: Throwable, resource: Address) = th match {
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

    private def requestFrom(path: Address, query: Query)(implicit credentials: Option[AuthToken]) = {
      val request = Get(iamUri.value.append(path).withQuery(query))
      credentials.map(request.addCredentials(_)).getOrElse(request)
    }
  }

  /**
    * Constructs an ''IamClient[Task]''
    *
    * @param iamUri the iam base uri
    * @return a new [[IamClient]] of [[Task]] context
    */
  final def task()(implicit iamUri: IamUri, as: ActorSystem): IamClient[Task] = {
    import ch.epfl.bluebrain.nexus.commons.http.HttpClient._
    import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
    import io.circe.generic.auto._
    implicit val mt                             = ActorMaterializer()
    implicit val ec                             = as.dispatcher
    implicit val ucl: UntypedHttpClient[Future] = akkaHttpClient
    implicit val aclsClient                     = withAkkaUnmarshaller[FullAccessControlList]
    implicit val userClient                     = withAkkaUnmarshaller[User]
    val underlying                              = fromFuture
    new IamClient[Task] {

      override def getCaller(filterGroups: Boolean = false)(implicit credentials: Option[AuthToken]): Task[Caller] =
        Task.deferFuture(underlying.getCaller(filterGroups))

      override def getAcls(resource: Address, parents: Boolean = false, self: Boolean = false)(
          implicit credentials: Option[AuthToken]): Task[FullAccessControlList] =
        Task.deferFuture(underlying.getAcls(resource, parents, self))
    }

  }

  private implicit def toAkka(token: AuthToken): OAuth2BearerToken = OAuth2BearerToken(token.value)

}
