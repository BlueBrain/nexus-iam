package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.Monad
import cats.effect.{Async, ConcurrentEffect}
import cats.implicits._
import ch.epfl.bluebrain.nexus.iam.acls.AclCommand._
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.AclState.{Current, Initial}
import ch.epfl.bluebrain.nexus.iam.acls.Acls._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.InitialAcl
import ch.epfl.bluebrain.nexus.iam.index.AclsIndex
import ch.epfl.bluebrain.nexus.iam.types.Caller
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka.{AkkaAggregate, AkkaSourcingConfig, PassivationStrategy, RetryStrategy}

class Acls[F[_]](agg: Agg[F], index: AclsIndex[F])(implicit F: Monad[F], clock: Clock, initAcl: InitialAcl) {

  /**
    * Overrides ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to replace
    */
  def replace(path: Path, rev: Long, acl: AccessControlList)(implicit caller: Caller): F[AclMetaOrRejection] =
    evaluate(path, ReplaceAcl(path, acl, rev, clock.instant(), caller.subject))

  /**
    * Appends ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to append
    */
  def append(path: Path, rev: Long, acl: AccessControlList)(implicit caller: Caller): F[AclMetaOrRejection] =
    evaluate(path, AppendAcl(path, acl, rev, clock.instant(), caller.subject))

  /**
    * Subtracts ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to subtract
    */
  def subtract(path: Path, rev: Long, acl: AccessControlList)(implicit caller: Caller): F[AclMetaOrRejection] =
    evaluate(path, SubtractAcl(path, acl, rev, clock.instant(), caller.subject))

  /**
    * Delete all ACL on a ''path''.
    *
    * @param path the target path for the ACL
    */
  def delete(path: Path, rev: Long)(implicit caller: Caller): F[AclMetaOrRejection] =
    evaluate(path, DeleteAcl(path, rev, clock.instant(), caller.subject))

  private def evaluate(path: Path, cmd: AclCommand)(implicit caller: Caller): F[AclMetaOrRejection] =
    checkPermissions(path).flatMap {
      case true =>
        agg
          .evaluateS(path.repr, cmd)
          .map(_.flatMap {
            case Initial    => Left(AclUnexpectedState(path, "Unexpected initial state"))
            case c: Current => Right(c.toResourceMetadata)
          })
      case false => F.pure(Left(AclUnauthorizedWrite(cmd.path)))
    }

  /**
    * Fetches the entire ACL for a ''path'' for all the identities on the latest revision
    *
    * @param path the target path for the ACL
    */
  def fetchUnsafe(path: Path): F[ResourceAccessControlList] =
    agg.currentState(path.repr).map(stateToAcl(path, _))

  /**
    * Fetches the entire ACL for a ''path'' for all the identities on the provided ''rev''
    *
    * @param path the target path for the ACL
    * @param rev  the revision to fetch
    */
  def fetchUnsafe(path: Path, rev: Long): F[ResourceAccessControlList] =
    agg
      .foldLeft[AclState](path.repr, Initial) {
        case (state, event) if event.rev <= rev => next(state, event)
        case (state, _)                         => state
      }
      .map(stateToAcl(path, _))

  /**
    * Fetches the entire ACL for a ''path'' for the provided ''identities'' on the latest revision
    *
    * @param path   the target path for the ACL
    * @param caller the caller which contains the identities to filter
    */
  def fetch(path: Path)(implicit caller: Caller): F[ResourceAccessControlList] =
    fetchUnsafe(path).map(_.map(_.filter(caller.identities)))

  /**
    * Fetches the entire ACL for a ''path'' for the provided ''identities'' on the provided ''rev''
    *
    * @param path   the target path for the ACL
    * @param rev    the revision to fetch
    * @param caller the caller which contains the identities to filter
    */
  def fetch(path: Path, rev: Long)(implicit caller: Caller): F[ResourceAccessControlList] =
    fetchUnsafe(path, rev).map(_.map(_.filter(caller.identities)))

  private def stateToAcl(path: Path, state: AclState): ResourceAccessControlList =
    (state, path) match {
      case (Initial, initAcl.path) => initAcl.acl
      case (Initial, _)            => initAcl.acl.map(_ => AccessControlList.empty)
      case (c: Current, _)         => c.toResource
    }

  /**
    * Fetches the [[AccessControlLists]] of the provided ''path'' with some filtering options.
    *
    * @param path      the path where the ACLs are going to be looked up
    * @param self      flag to decide whether or not ancestor other identities than the provided ones should be included in the response
    * @param caller    the caller that contains the provided identities
    */
  def list(path: Path, self: Boolean)(implicit caller: Caller): F[AccessControlLists] =
    index.get(path, ancestors = true, self)(caller.identities)

  private def checkPermissions(path: Path)(implicit caller: Caller): F[Boolean] =
    fetch(path).flatMap { c =>
      val hasPerms = c.value.value.exists {
        case (_, perms) => perms.contains(writePermission)
      }
      if (hasPerms) F.pure(true)
      else if (path.isEmpty) F.pure(false)
      else checkPermissions(path.tail)
    }
}

@SuppressWarnings(Array("OptionGet"))
object Acls {

  /**
    * Construct an ''Acls'' wrapped on an ''F'' type based on akka clustered [[Aggregate]].
    */
  def apply[F[_]: ConcurrentEffect](index: AclsIndex[F])(implicit cl: Clock = Clock.systemUTC(),
                                                         ac: AppConfig,
                                                         sc: AkkaSourcingConfig,
                                                         as: ActorSystem,
                                                         mt: ActorMaterializer): F[Acls[F]] = {
    val aggF: F[Aggregate[F, String, AclEvent, AclState, AclCommand, AclRejection]] = AkkaAggregate.sharded(
      "acls",
      AclState.Initial,
      next,
      evaluate[F],
      PassivationStrategy.immediately[AclState, AclCommand],
      RetryStrategy.never,
      sc,
      ac.cluster.shards
    )
    aggF.map(new Acls(_, index))
  }

  /**
    * Construct an ''Acls'' wrapped on an ''F'' type based on an in memory [[Aggregate]].
    */
  def inMemory[F[_]: ConcurrentEffect](index: AclsIndex[F])(implicit cl: Clock, initAcl: InitialAcl): F[Acls[F]] = {
    val aggF: F[Aggregate[F, String, AclEvent, AclState, AclCommand, AclRejection]] =
      Aggregate.inMemory[F, String]("acls", Initial, next, evaluate[F])
    aggF.map(new Acls(_, index))
  }

  def next(state: AclState, ev: AclEvent): AclState = (state, ev) match {

    case (Initial, AclReplaced(p, acl, 1L, instant, identity)) =>
      Current(p, acl, 1L, instant, instant, identity, identity)

    case (Initial, _) => Initial

    case (c: Current, AclReplaced(p, acl, rev, instant, identity)) =>
      c.copy(p, acl, rev, updatedAt = instant, updatedBy = identity)

    case (c: Current, AclAppended(p, acl, rev, instant, identity)) =>
      c.copy(p, c.acl ++ acl, rev, updatedAt = instant, updatedBy = identity)

    case (c: Current, AclSubtracted(p, acl, rev, instant, identity)) =>
      c.copy(p, c.acl -- acl, rev, updatedAt = instant, updatedBy = identity)

    case (c: Current, AclDeleted(p, rev, instant, identity)) =>
      c.copy(p, AccessControlList.empty, rev, updatedAt = instant, updatedBy = identity)
  }

  def evaluate[F[_]: Async](state: AclState, command: AclCommand): F[EventOrRejection] = {
    val F = implicitly[Async[F]]

    def replaced(c: ReplaceAcl): EventOrRejection = state match {
      case _ if c.acl.hasVoidPermissions  => Left(AclInvalidEmptyPermissions(c.path))
      case Initial if c.rev == 0          => Right(AclReplaced(c.path, c.acl, 1L, c.instant, c.subject))
      case Initial                        => Left(AclIncorrectRev(c.path, c.rev))
      case ss: Current if c.rev != ss.rev => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current                     => Right(AclReplaced(c.path, c.acl, c.rev + 1, c.instant, c.subject))
    }

    def append(c: AppendAcl): EventOrRejection = state match {
      case Initial                                  => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev           => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions   => Left(AclInvalidEmptyPermissions(c.path))
      case ss: Current if ss.acl ++ c.acl == ss.acl => Left(NothingToBeUpdated(c.path))
      case _: Current                               => Right(AclAppended(c.path, c.acl, c.rev + 1, c.instant, c.subject))
    }

    def subtract(c: SubtractAcl): EventOrRejection = state match {
      case Initial                                  => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev           => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions   => Left(AclInvalidEmptyPermissions(c.path))
      case ss: Current if ss.acl -- c.acl == ss.acl => Left(NothingToBeUpdated(c.path))
      case _: Current                               => Right(AclSubtracted(c.path, c.acl, c.rev + 1, c.instant, c.subject))
    }

    def delete(c: DeleteAcl): EventOrRejection = state match {
      case Initial                                          => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev                   => Left(AclIncorrectRev(c.path, c.rev))
      case ss: Current if ss.acl == AccessControlList.empty => Left(AclIsEmpty(c.path))
      case _: Current                                       => Right(AclDeleted(c.path, c.rev + 1, c.instant, c.subject))
    }

    command match {
      case c: ReplaceAcl  => F.pure(replaced(c))
      case c: AppendAcl   => F.pure(append(c))
      case c: SubtractAcl => F.pure(subtract(c))
      case c: DeleteAcl   => F.pure(delete(c))
    }
  }
}
