package ch.epfl.bluebrain.nexus.iam.service.routes

import java.time.Clock

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.Credentials
import ch.epfl.bluebrain.nexus.commons.http.ContextUri
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
import ch.epfl.bluebrain.nexus.iam.elastic.SimpleIdentitySerialization.identityDecoder
import ch.epfl.bluebrain.nexus.iam.service.io.JsonLdSerialization.identityEncoder
import ch.epfl.bluebrain.nexus.service.kamon.directives.TracingDirectives
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.WrongOrInvalidJson
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.{AnonymousUser, User}
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.acls.CallerCtx._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControl, AccessControlList, Permission, Permissions}
import ch.epfl.bluebrain.nexus.iam.core.groups.UsedGroups
import ch.epfl.bluebrain.nexus.iam.elastic.query.FilterAcls
import ch.epfl.bluebrain.nexus.iam.service.auth.AuthenticationFailure.UnauthorizedCaller
import ch.epfl.bluebrain.nexus.iam.service.auth.ClaimExtractor
import ch.epfl.bluebrain.nexus.iam.service.auth.ClaimExtractor.{JsonSyntax, OAuth2BearerTokenSyntax}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.ContextConfig
import ch.epfl.bluebrain.nexus.iam.service.directives.AclDirectives._
import ch.epfl.bluebrain.nexus.iam.service.groups.UserGroupsOps._
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport.config
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport.printer
import ch.epfl.bluebrain.nexus.iam.service.routes.AclsRoutes._
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejection._
import ch.epfl.bluebrain.nexus.iam.service.types.Subtract
import ch.epfl.bluebrain.nexus.iam.service.types.{ApiUri, PartialUpdate}
import ch.epfl.bluebrain.nexus.service.http.Path
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.auto._

import scala.concurrent.{ExecutionContext, Future}

/**
  * HTTP routes for ACL specific functionality.
  *
  * @param acl        the ACL operations bundle
  * @param aclsFilter queries for ACLs
  */
class AclsRoutes(acl: Acls[Future], aclsFilter: FilterAcls[Future], usedGroups: UsedGroups[Future])(
    implicit clock: Clock,
    ce: ClaimExtractor,
    api: ApiUri,
    contexts: ContextConfig,
    orderedKeys: OrderedKeys,
    tracing: TracingDirectives)
    extends DefaultRoutes("acls", contexts.error) {

  private implicit val enc: Encoder[Identity] = identityEncoder(api.base)
  private implicit val iamContext: ContextUri = contexts.iam

  override def apiRoutes: Route =
    extractExecutionContext { implicit ec =>
      extractResourcePath { path =>
        authenticateOAuth2Async("*", authenticator).withAnonymousUser(AnonymousUser()) { implicit user =>
          put {
            entity(as[AccessControlList]) { list =>
              authorizeAsync(check(path, user, Permission.Own)) {
                tracing.trace("addPermissions") {
                  onSuccess(acl.add(path, list)) {
                    complete(StatusCodes.OK -> HttpEntity.Empty)
                  }
                }
              }
            }
          } ~
            patch {
              (entity(as[PartialUpdate]) & authorizeAsync(check(path, user, Permission.Own))) {
                case Subtract(identity, permissions) =>
                  tracing.trace("subtractPermissions") {
                    onSuccess(acl.subtract(path, identity, permissions)) { result =>
                      complete(StatusCodes.OK -> AccessControl(identity, result))
                    }
                  }
              }
            } ~
            delete {
              authorizeAsync(check(path, user, Permission.Own)) {
                tracing.trace("deletePermissions") {
                  onSuccess(acl.clear(path)) {
                    complete(StatusCodes.NoContent)
                  }
                }
              }
            } ~
            get {
              parameters(('self.as[Boolean] ? false, 'parents.as[Boolean] ? false)) { (self, parents) =>
                tracing.trace("getPermissions") {
                  onSuccess(aclsFilter(path, parents, self)) { result =>
                    complete(StatusCodes.OK -> result)
                  }
                }
              }
            }
        }
      }
    }

  private def authenticator(implicit ec: ExecutionContext): AsyncAuthenticator[User] = {
    case Credentials.Missing => Future.successful(None)
    case Credentials.Provided(token) =>
      val cred = OAuth2BearerToken(token)
      cred.extractClaim
        .flatMap {
          case (client, json) =>
            json
              .extractUser(client.config)
              .recoverWith {
                case _ => client.getUser(cred)
              }
              .flatMap(u => usedGroups.fetch(client.config.realm).map(u.filterGroups))
        }
        .map(Some.apply)
        .recover { case UnauthorizedCaller => None }

  }

  /**
    * Checks whether the ''user'' has __any__ of the ''permissions'' argument on this resource ''path''.
    *
    * @return a future true if the user has one of the required permissions, false otherwise
    */
  private def check(path: Path, user: User, permissions: Permission*)(implicit ec: ExecutionContext): Future[Boolean] =
    acl.retrieve(path, user.identities).map(_.values.exists(_.containsAny(Permissions(permissions: _*))))

}

object AclsRoutes {

  /**
    * Constructs a new ''AclsRoutes'' instance that defines the http routes specific to ACL endpoints.
    *
    * @param acl        the ACL operation bundle
    * @param aclsFilter queries for ACLs
    */
  def apply(acl: Acls[Future], aclsFilter: FilterAcls[Future], usedGroups: UsedGroups[Future])(
      implicit clock: Clock,
      ce: ClaimExtractor,
      api: ApiUri,
      contexts: ContextConfig,
      orderedKeys: OrderedKeys,
      tracing: TracingDirectives): AclsRoutes =
    new AclsRoutes(acl, aclsFilter, usedGroups)

  implicit val decoder: Decoder[AccessControl] = Decoder.instance { cursor =>
    val fields = cursor.keys.toSeq.flatten
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
