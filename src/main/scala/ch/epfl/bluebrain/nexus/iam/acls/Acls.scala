package ch.epfl.bluebrain.nexus.iam.acls

import cats.Functor
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.acls.AclCommand._
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.AclState.{Current, Initial}
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.service.http.Path

class Acls[F[_]: Functor] {

  /**
    * Creates or appends ''acl'' on a ''path''
    *
    * @param path the path
    * @param acl  the identity to permissions mapping to create
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def add(path: Path, acl: AccessControlList)(implicit ctx: Identity): F[Unit] = ???

  /**
    * Subtracts ''permissions'' on a ''path'' for an ''identity''.
    *
    * @param path        the path
    * @param identity    the target identity
    * @param permissions the permissions to subtract
    * @param ctx         the implicit identity context calling this action
    * @return the resulting permissions in an ''F[_]'' context
    */
  def subtract(path: Path, identity: Identity, permissions: Set[Permission])(
      implicit ctx: Identity): F[Set[Permission]] = ???

  /**
    * Overrides ''acl'' on a ''path''
    *
    * @param path the path
    * @param acl  the identity to permissions mapping to create
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def replace(path: Path, acl: AccessControlList)(implicit ctx: Identity): F[Unit] = ???

  /**
    * Clears all permissions on a ''path''.
    *
    * @param path the path
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def clear(path: Path)(implicit ctx: Identity): F[Unit] = ???

  /**
    * Fetches the entire ACLs for a ''path''.
    *
    * @param path the path for where to fetch the ACls
    */
  private[acls] def fetchUnsafe(path: Path): F[AccessControlList] = ???

  /**
    * Fetches the entire ACL for a ''path'' for the provided ''identities''.
    *
    * @param path the path for where to fetch the ACls
    * @param identities the identities to filter
    */
  def fetch(path: Path)(implicit identities: Set[Identity]): F[AccessControlList] =
    fetchUnsafe(path).map(_.filter(identities))
}
@SuppressWarnings(Array("OptionGet"))
object Acls {

  private val writePermission = Permission("acls/write").get

  private type RejectionOrEvent = Either[AclRejection, AclEvent]

  def next(state: AclState, ev: AclEvent): AclState = (state, ev) match {

    case (Initial, AclReplaced(p, acl, 0L, instant, identity)) =>
      Current(p, acl, 0L, instant, instant, identity, identity)

    case (Initial, _) => Initial

    case (c: Current, AclReplaced(p, acl, rev, instant, identity)) =>
      c.copy(p, acl, rev, updated = instant, updatedBy = identity)

    case (c: Current, AclAppended(p, acl, rev, instant, identity)) =>
      c.copy(p, c.acl ++ acl, rev, updated = instant, updatedBy = identity)

    case (c: Current, AclSubtracted(p, acl, rev, instant, identity)) =>
      c.copy(p, c.acl -- acl, rev, updated = instant, updatedBy = identity)

    case (c: Current, AclDeleted(p, rev, instant, identity)) =>
      c.copy(p, AccessControlList.empty, rev, updated = instant, updatedBy = identity)
  }

  def evaluate[F[_]](acls: Acls[F])(state: AclState, command: AclCommand)(
      implicit F: Async[F],
      identities: Set[Identity]): F[RejectionOrEvent] = {

    def hasWritePermissions(path: Path): F[Boolean] = {
      acls.fetchUnsafe(path).flatMap { curr =>
        val hasPerms = curr.value.exists {
          case (identity, perms) => perms.contains(writePermission) && identities.contains(identity)
        }
        if (hasPerms) F.pure(true)
        else if (path.isEmpty) F.pure(false)
        else hasWritePermissions(path.tail)
      }
    }

    def permissionsCheck(path: Path)(onSuccess: => AclEvent): F[RejectionOrEvent] =
      hasWritePermissions(path).map {
        case true  => Right(onSuccess)
        case false => Left(AclUnauthorizedWrite(path))
      }

    def replaced(c: ReplaceAcl): F[RejectionOrEvent] = state match {
      case Initial if c.rev == 0                  => permissionsCheck(c.path)(AclReplaced(c.path, c.acl, c.rev, c.instant, c.identity))
      case Initial                                => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case ss: Current if c.rev != ss.rev + 1     => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case _: Current if c.acl.hasVoidPermissions => F.pure(Left(AclInvalidEmptyPermissions(c.path)))
      case _: Current                             => permissionsCheck(c.path)(AclReplaced(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def append(c: AppendAcl): F[RejectionOrEvent] = state match {
      case Initial                                  => F.pure(Left(AclNotFound(c.path)))
      case ss: Current if c.rev != ss.rev + 1       => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case _: Current if c.acl.hasVoidPermissions   => F.pure(Left(AclInvalidEmptyPermissions(c.path)))
      case ss: Current if ss.acl ++ c.acl == ss.acl => F.pure(Left(AclAlreadyExists(c.path)))
      case _: Current                               => permissionsCheck(c.path)(AclAppended(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def subtract(c: SubtractAcl): F[RejectionOrEvent] = state match {
      case Initial                                  => F.pure(Left(AclNotFound(c.path)))
      case ss: Current if c.rev != ss.rev + 1       => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case _: Current if c.acl.hasVoidPermissions   => F.pure(Left(AclInvalidEmptyPermissions(c.path)))
      case ss: Current if ss.acl -- c.acl == ss.acl => F.pure(Left(AclAlreadyExists(c.path)))
      case _: Current                               => permissionsCheck(c.path)(AclSubtracted(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def delete(c: DeleteAcl): F[RejectionOrEvent] = state match {
      case Initial                                          => F.pure(Left(AclNotFound(c.path)))
      case ss: Current if c.rev != ss.rev + 1               => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case ss: Current if ss.acl == AccessControlList.empty => F.pure(Left(AclIsEmpty(c.path)))
      case _: Current                                       => permissionsCheck(c.path)(AclDeleted(c.path, c.rev, c.instant, c.identity))
    }

    command match {
      case c: ReplaceAcl  => replaced(c)
      case c: AppendAcl   => append(c)
      case c: SubtractAcl => subtract(c)
      case c: DeleteAcl   => delete(c)
    }
  }

}
