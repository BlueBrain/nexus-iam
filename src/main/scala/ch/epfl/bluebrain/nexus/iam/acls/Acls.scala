package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.{Applicative, Monad}
import cats.effect.{Clock, Effect, Timer}
import cats.effect.syntax.all._
import cats.implicits._
import ch.epfl.bluebrain.nexus.iam.acls.AccessControlList.empty
import ch.epfl.bluebrain.nexus.iam.acls.AclCommand._
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.AclState.{Current, Initial}
import ch.epfl.bluebrain.nexus.iam.acls.Acls._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{AclsConfig, HttpConfig}
import ch.epfl.bluebrain.nexus.iam.index.{AclsIndex, InMemoryAclsTree}
import ch.epfl.bluebrain.nexus.iam.io.TaggingAdapter
import ch.epfl.bluebrain.nexus.iam.permissions.Permissions
import ch.epfl.bluebrain.nexus.iam.syntax._
import ch.epfl.bluebrain.nexus.iam.types.IamError.{AccessDenied, UnexpectedInitialState}
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.{Caller, MonadThrowable, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.service.indexer.persistence.{IndexerConfig, SequentialTagIndexer}
import ch.epfl.bluebrain.nexus.service.indexer.persistence.OffsetStorage.Volatile
import ch.epfl.bluebrain.nexus.sourcing.akka.AkkaAggregate

//noinspection RedundantDefaultArgument
class Acls[F[_]](
    agg: Agg[F],
    perms: F[Permissions[F]],
    index: AclsIndex[F],
)(implicit F: MonadThrowable[F], http: HttpConfig) {

  /**
    * Overrides ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to replace
    */
  def replace(path: Path, rev: Long, acl: AccessControlList)(implicit caller: Caller): F[MetaOrRejection] =
    check(path, write) *> eval(path, ReplaceAcl(path, acl, rev, caller.subject)) <* updateIndex(path)

  /**
    * Appends ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to append
    */
  def append(path: Path, rev: Long, acl: AccessControlList)(implicit caller: Caller): F[MetaOrRejection] =
    check(path, write) *> eval(path, AppendAcl(path, acl, rev, caller.subject)) <* updateIndex(path)

  /**
    * Subtracts ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to subtract
    */
  def subtract(path: Path, rev: Long, acl: AccessControlList)(implicit caller: Caller): F[MetaOrRejection] =
    check(path, write) *> eval(path, SubtractAcl(path, acl, rev, caller.subject)) <* updateIndex(path)

  /**
    * Delete all ACL on a ''path''.
    *
    * @param path the target path for the ACL
    */
  def delete(path: Path, rev: Long)(implicit caller: Caller): F[MetaOrRejection] =
    check(path, write) *> eval(path, DeleteAcl(path, rev, caller.subject)) <* updateIndex(path)

  private def eval(path: Path, cmd: AclCommand): F[MetaOrRejection] =
    agg
      .evaluateS(path.asString, cmd)
      .flatMap {
        case Left(rej)         => F.pure(Left(rej))
        case Right(Initial)    => F.raiseError(UnexpectedInitialState(path.toIri))
        case Right(c: Current) => F.pure(Right(c.resourceMetadata))
      }

  /**
    * Fetches the entire ACL for a ''path'' on the provided ''rev''.
    *
    * @param path   the target path for the ACL
    * @param rev    the revision to fetch
    * @param self   flag to decide whether or not ACLs of other identities than the provided ones should be included in the response.
    *               This is constrained by the current caller having ''acls/read'' permissions on the provided ''path'' or it's parents
    */
  def fetch(path: Path, rev: Long, self: Boolean)(implicit caller: Caller): F[ResourceOpt] =
    if (self) fetchUnsafe(path, rev).map(filterSelf)
    else check(path, read) *> fetchUnsafe(path, rev)

  /**
    * Fetches the entire ACL for a ''path''.
    *
    * @param path the target path for the ACL
    * @param self flag to decide whether or not ACLs of other identities than the provided ones should be included in the response.
    *             This is constrained by the current caller having ''acls/read'' permissions on the provided ''path'' or it's parents
    */
  def fetch(path: Path, self: Boolean)(implicit caller: Caller): F[ResourceOpt] =
    if (self) fetchUnsafe(path).map(filterSelf)
    else check(path, read) *> fetchUnsafe(path)

  /**
    * Fetches the [[AccessControlLists]] of the provided ''path'' with some filtering options.
    *
    * @param path      the path where the ACLs are going to be looked up
    * @param ancestors flag to decide whether or not ancestor paths should be included in the response
    * @param self      flag to decide whether or not ancestor other identities than the provided ones should be included in the response
    * @param caller    the caller that contains the provided identities
    */
  def list(path: Path, ancestors: Boolean, self: Boolean)(implicit caller: Caller): F[AccessControlLists] =
    index.get(path, ancestors, self)(caller.identities)

  private def fetchUnsafe(path: Path): F[ResourceOpt] =
    agg.currentState(path.asString).flatMap(stateToAcl(path, _))

  private def fetchUnsafe(path: Path, rev: Long): F[ResourceOpt] =
    agg
      .foldLeft[AclState](path.asString, Initial) {
        case (state, event) if event.rev <= rev => next(state, event)
        case (state, _)                         => state
      }
      .flatMap(stateToAcl(path, _))

  private def stateToAcl(path: Path, state: AclState): F[ResourceOpt] =
    (state, path) match {
      case (Initial, Path./) =>
        perms.map { p =>
          val acl = AccessControlList(Anonymous -> p.minimum)
          Some(Current(Path./, acl, 0L, Instant.EPOCH, Instant.EPOCH, Anonymous, Anonymous).resource)
        }
      case (Initial, _)    => F.pure(None)
      case (c: Current, _) => F.pure(Some(c.resource))
    }

  private def check(path: Path, permission: Permission)(implicit caller: Caller): F[Unit] =
    hasPermission(path, permission, ancestors = true)
      .ifM(F.unit, F.raiseError(AccessDenied(path.toIri, permission)))

  private def filterSelf(opt: ResourceOpt)(implicit caller: Caller): ResourceOpt =
    opt.map(_.map(acl => acl.filter(caller.identities)))

  def hasPermission(path: Path, perm: Permission, ancestors: Boolean = true)(implicit caller: Caller): F[Boolean] = {
    fetchUnsafe(path).flatMap {
      case Some(res) if res.value.hasPermission(caller.identities, perm) => F.pure(true)
      case _ if path == Path./                                           => F.pure(false)
      case _ if ancestors                                                => hasPermission(path.parent, perm)
      case _                                                             => F.pure(false)
    }
  }

  private[acls] def updateIndex(path: Path): F[Unit] = {
    fetchUnsafe(path).flatMap {
      case Some(res) => index.replace(path, res) *> F.unit
      case None      => F.unit
    }
  }

}

object Acls {

  /**
    * Constructs a new acls aggregate.
    */
  def aggregate[F[_]: Effect: Timer: Clock](
      perms: F[Permissions[F]])(implicit as: ActorSystem, mt: ActorMaterializer, ac: AclsConfig): F[Agg[F]] =
    AkkaAggregate.sharded[F](
      "acls",
      AclState.Initial,
      next,
      evaluate[F](perms),
      ac.sourcing.passivationStrategy(),
      ac.sourcing.retryStrategy,
      ac.sourcing.akkaSourcingConfig,
      ac.sourcing.shards
    )

  /**
    * Constructs an ACL index.
    */
  def index[F[_]: Applicative]: AclsIndex[F] =
    InMemoryAclsTree[F]()

  /**
    * Constructs a new ACLs api using the provided aggregate, a lazy reference to the permissions api and an index.
    *
    * @param agg   the acl aggregate
    * @param perms a lazy reference to the permissions api
    * @param index an acl index
    */
  def apply[F[_]: MonadThrowable](
      agg: Agg[F],
      perms: F[Permissions[F]],
      index: AclsIndex[F]
  )(implicit http: HttpConfig): Acls[F] =
    new Acls(agg, perms, index)

  /**
    * Constructs a new ACLs api using the provided aggregate, a lazy reference to the permissions api and an index.
    *
    * @param perms a lazy reference to the permissions api
    */
  def apply[F[_]: Effect: Timer: Clock](perms: F[Permissions[F]])(
      implicit
      as: ActorSystem,
      mt: ActorMaterializer,
      http: HttpConfig,
      ac: AclsConfig
  ): F[Acls[F]] =
    delay(aggregate(perms), perms, index)

  /**
    * Constructs a new ACLs api using the provided aggregate, a lazy reference to the permissions api and an index.
    *
    * @param agg   the acl aggregate
    * @param perms a lazy reference to the permissions api
    * @param index an acl index
    */
  def delay[F[_]: MonadThrowable](
      agg: F[Agg[F]],
      perms: F[Permissions[F]],
      index: AclsIndex[F]
  )(implicit http: HttpConfig): F[Acls[F]] =
    agg.map(apply(_, perms, index))

  /**
    * Builds a process for automatically updating the acl index with the latest events logged.
    *
    * @param acls the acls API
    */
  def indexer[F[_]](acls: Acls[F])(implicit F: Effect[F], as: ActorSystem, ac: AclsConfig): F[Unit] = {
    val indexFn = (events: List[Event]) => {
      val value = events.traverse(e => acls.updateIndex(e.path)) *> F.unit
      value.toIO.unsafeToFuture()
    }
    val cfg = IndexerConfig.builder
      .name("acl-index")
      .batch(ac.indexing.batch, ac.indexing.batchTimeout)
      .plugin(ac.sourcing.queryJournalPlugin)
      .tag(TaggingAdapter.aclEventTag)
      .retry(ac.indexing.retry.maxCount, ac.indexing.retry.strategy)
      .index[AclEvent](indexFn)
      .build
      .asInstanceOf[IndexerConfig[Event, Volatile]]
    F.delay(SequentialTagIndexer.start(cfg)) *> F.unit
  }

  private[acls] def next(state: State, event: Event): AclState = {
    def replaced(e: AclReplaced): State = state match {
      case Initial    => Current(e.path, e.acl, 1L, e.instant, e.instant, e.subject, e.subject)
      case c: Current => c.copy(acl = e.acl, rev = e.rev, updatedAt = e.instant, updatedBy = e.subject)
    }
    def appended(e: AclAppended): State = state match {
      case Initial    => Current(e.path, e.acl, 1L, e.instant, e.instant, e.subject, e.subject)
      case c: Current => c.copy(acl = c.acl ++ e.acl, rev = e.rev, updatedAt = e.instant, updatedBy = e.subject)
    }
    def subtracted(e: AclSubtracted): State = state match {
      case Initial    => Current(e.path, e.acl, 1L, e.instant, e.instant, e.subject, e.subject)
      case c: Current => c.copy(acl = c.acl -- e.acl, rev = e.rev, updatedAt = e.instant, updatedBy = e.subject)
    }
    def deleted(e: AclDeleted): State = state match {
      case Initial    => Initial
      case c: Current => c.copy(acl = empty, rev = e.rev, updatedAt = e.instant, updatedBy = e.subject)
    }
    event match {
      case ev: AclReplaced   => replaced(ev)
      case ev: AclAppended   => appended(ev)
      case ev: AclSubtracted => subtracted(ev)
      case ev: AclDeleted    => deleted(ev)
    }
  }

  private def evaluate[F[_]: Monad: Clock](perms: F[Permissions[F]])(state: State,
                                                                     cmd: Command): F[EventOrRejection] = {
    val F = implicitly[Monad[F]]
    val C = implicitly[Clock[F]]
    def accept(f: Instant => Event): F[EventOrRejection] =
      C.realTime(TimeUnit.MILLISECONDS).map(rtl => Right(f(Instant.ofEpochMilli(rtl))))
    def reject(rejection: Rejection): F[EventOrRejection] =
      F.pure(Left(rejection))
    def acceptChecking(acl: AccessControlList)(f: Instant => Event): F[EventOrRejection] =
      perms.flatMap(_.effectivePermissionsUnsafe.flatMap {
        case ps if acl.permissions.subsetOf(ps) => accept(f)
        case ps                                 => reject(UnknownPermissions(acl.permissions -- ps))
      })

    def replaced(c: ReplaceAcl): F[EventOrRejection] = state match {
      case _ if c.acl.hasVoidPermissions  => reject(AclCannotContainEmptyPermissionCollection(c.path))
      case Initial if c.rev == 0L         => acceptChecking(c.acl)(AclReplaced(c.path, c.acl, 1L, _, c.subject))
      case Initial                        => reject(IncorrectRev(c.path, c.rev))
      case ss: Current if c.rev != ss.rev => reject(IncorrectRev(c.path, c.rev))
      case _: Current                     => acceptChecking(c.acl)(AclReplaced(c.path, c.acl, c.rev + 1, _, c.subject))
    }
    def append(c: AppendAcl): F[EventOrRejection] = state match {
      case Initial if c.rev == 0L                 => acceptChecking(c.acl)(AclAppended(c.path, c.acl, c.rev + 1, _, c.subject))
      case Initial                                => reject(IncorrectRev(c.path, c.rev))
      case s: Current if c.rev != s.rev           => reject(IncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions => reject(AclCannotContainEmptyPermissionCollection(c.path))
      case s: Current if s.acl ++ c.acl == s.acl  => reject(NothingToBeUpdated(c.path))
      case _: Current                             => acceptChecking(c.acl)(AclAppended(c.path, c.acl, c.rev + 1, _, c.subject))
    }
    def subtract(c: SubtractAcl): F[EventOrRejection] = state match {
      case Initial                                => reject(AclNotFound(c.path))
      case s: Current if c.rev != s.rev           => reject(IncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions => reject(AclCannotContainEmptyPermissionCollection(c.path))
      case s: Current if s.acl -- c.acl == s.acl  => reject(NothingToBeUpdated(c.path))
      case _: Current                             => acceptChecking(c.acl)(AclSubtracted(c.path, c.acl, c.rev + 1, _, c.subject))
    }
    def delete(c: DeleteAcl): F[EventOrRejection] = state match {
      case Initial                      => reject(AclNotFound(c.path))
      case s: Current if c.rev != s.rev => reject(IncorrectRev(c.path, c.rev))
      case s: Current if s.acl == empty => reject(AclIsEmpty(c.path))
      case _: Current                   => accept(AclDeleted(c.path, c.rev + 1, _, c.subject))
    }

    cmd match {
      case c: ReplaceAcl  => replaced(c)
      case c: AppendAcl   => append(c)
      case c: SubtractAcl => subtract(c)
      case c: DeleteAcl   => delete(c)
    }
  }
}
