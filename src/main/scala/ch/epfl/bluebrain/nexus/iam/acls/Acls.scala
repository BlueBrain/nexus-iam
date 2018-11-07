package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Clock

import cats.Functor
import cats.effect.Async
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.acls.AclCommand._
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.AclState.{Current, Initial}
import ch.epfl.bluebrain.nexus.iam.acls.Acls._
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.ResourceMeta
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.sourcing.Aggregate

class Acls[F[_]: Functor](agg: Agg[F], clock: Clock) {

  private val types: Set[AbsoluteIri] = Set(nxv.AccessControlList)

  /**
    * Overrides ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to replace
    */
  def replace(path: Path, rev: Long, acl: AccessControlList)(implicit ctx: Identity): F[AclMetaOrRejection] =
    evaluate(path, ReplaceAcl(path, acl, rev, clock.instant(), ctx))

  /**
    * Appends ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to append
    */
  def append(path: Path, rev: Long, acl: AccessControlList)(implicit ctx: Identity): F[AclMetaOrRejection] =
    evaluate(path, AppendAcl(path, acl, rev, clock.instant(), ctx))

  /**
    * Subtracts ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to subtract
    */
  def subtract(path: Path, rev: Long, acl: AccessControlList)(implicit ctx: Identity): F[AclMetaOrRejection] =
    evaluate(path, SubtractAcl(path, acl, rev, clock.instant(), ctx))

  /**
    * Delete all ACL on a ''path''.
    *
    * @param path the target path for the ACL
    */
  def delete(path: Path, rev: Long)(implicit ctx: Identity): F[AclMetaOrRejection] =
    evaluate(path, DeleteAcl(path, rev, clock.instant(), ctx))

  //TODO: When the evaluate method will return the state, use it to fetch the current updatedBy and updatedAt
  private def evaluate(path: Path, cmd: AclCommand): F[AclMetaOrRejection] =
    agg
      .evaluate(path.repr, cmd)
      .map(_.right.map(ev => ResourceMeta(ev.path, ev.rev, types, ev.identity, ev.identity, ev.instant, ev.instant)))

  /**
    * Fetches the entire ACL for a ''path'' for all the identities on the latest revision
    *
    * @param path the target path for the ACL
    */
  def fetchUnsafe(path: Path): F[AccessControlList] =
    agg.currentState(path.repr).map(stateToAcl)

  /**
    * Fetches the entire ACL for a ''path'' for all the identities on the provided ''rev''
    *
    * @param path the target path for the ACL
    * @param rev  the revision to fetch
    */
  def fetchUnsafe(path: Path, rev: Long): F[AccessControlList] =
    agg
      .foldLeft[AclState](path.repr, Initial) {
        case (state, event) if event.rev <= rev => next(state, event)
        case (state, _)                         => state
      }
      .map(stateToAcl)

  /**
    * Fetches the entire ACL for a ''path'' for the provided ''identities'' on the latest revision
    *
    * @param path       the target path for the ACL
    * @param identities the identities to filter
    */
  def fetch(path: Path)(implicit identities: Set[Identity]): F[AccessControlList] =
    fetchUnsafe(path).map(_.filter(identities))

  /**
    * Fetches the entire ACL for a ''path'' for the provided ''identities'' on the provided ''rev''
    *
    * @param path       the target path for the ACL
    * @param rev        the revision to fetch
    * @param identities the identities to filter
    */
  def fetch(path: Path, rev: Long)(implicit identities: Set[Identity]): F[AccessControlList] =
    fetchUnsafe(path, rev).map(_.filter(identities))

  private def stateToAcl(state: AclState): AccessControlList =
    state match {
      case Initial    => AccessControlList.empty
      case c: Current => c.acl
    }
}
@SuppressWarnings(Array("OptionGet"))
object Acls {

  /**
    * Aggregate type for resources.
    *
    * @tparam F the effect type under which the aggregate operates
    */
  type Agg[F[_]] = Aggregate[F, String, AclEvent, AclState, AclCommand, AclRejection]

  private type EventOrRejection   = Either[AclRejection, AclEvent]
  private type AclMetaOrRejection = Either[AclRejection, ResourceMeta[Path]]

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

  def evaluate[F[_]](state: AclState, command: AclCommand)(implicit F: Async[F]): F[EventOrRejection] = {

    def replaced(c: ReplaceAcl): EventOrRejection = state match {
      case Initial if c.rev == 0                  => Right(AclReplaced(c.path, c.acl, c.rev, c.instant, c.identity))
      case Initial                                => Left(AclIncorrectRev(c.path, c.rev))
      case ss: Current if c.rev != ss.rev + 1     => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions => Left(AclInvalidEmptyPermissions(c.path))
      case _: Current                             => Right(AclReplaced(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def append(c: AppendAcl): EventOrRejection = state match {
      case Initial                                  => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1       => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions   => Left(AclInvalidEmptyPermissions(c.path))
      case ss: Current if ss.acl ++ c.acl == ss.acl => Left(AclAlreadyExists(c.path))
      case _: Current                               => Right(AclAppended(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def subtract(c: SubtractAcl): EventOrRejection = state match {
      case Initial                                  => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1       => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions   => Left(AclInvalidEmptyPermissions(c.path))
      case ss: Current if ss.acl -- c.acl == ss.acl => Left(AclAlreadyExists(c.path))
      case _: Current                               => Right(AclSubtracted(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def delete(c: DeleteAcl): EventOrRejection = state match {
      case Initial                                          => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1               => Left(AclIncorrectRev(c.path, c.rev))
      case ss: Current if ss.acl == AccessControlList.empty => Left(AclIsEmpty(c.path))
      case _: Current                                       => Right(AclDeleted(c.path, c.rev, c.instant, c.identity))
    }

    command match {
      case c: ReplaceAcl  => F.pure(replaced(c))
      case c: AppendAcl   => F.pure(append(c))
      case c: SubtractAcl => F.pure(subtract(c))
      case c: DeleteAcl   => F.pure(delete(c))
    }
  }

}
