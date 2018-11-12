package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Clock

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.Functor
import cats.effect.concurrent.Deferred
import cats.effect.{Async, ConcurrentEffect}
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.acls.AclCommand._
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.AclState.{Current, Initial}
import ch.epfl.bluebrain.nexus.iam.acls.Acls._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.InitialAcl
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.syntax._
import ch.epfl.bluebrain.nexus.iam.types.{Permission, ResourceMetadata}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import ch.epfl.bluebrain.nexus.sourcing.akka.{AkkaAggregate, AkkaSourcingConfig, PassivationStrategy, RetryStrategy}

class Acls[F[_]: Functor](agg: Agg[F])(implicit clock: Clock, initAcl: InitialAcl) {

  private val types: Set[AbsoluteIri] = Set(nxv.AccessControlList)

  private val base: Iri.AbsoluteIri = url"https://bluebrain.github.io/nexus/acls/".value

  /**
    * Overrides ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to replace
    */
  def replace(path: Path, rev: Long, acl: AccessControlList)(
      implicit identities: Set[Identity]): F[AclMetaOrRejection] =
    evaluate(path, ReplaceAcl(path, acl, rev, clock.instant(), identities))

  /**
    * Appends ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to append
    */
  def append(path: Path, rev: Long, acl: AccessControlList)(implicit identities: Set[Identity]): F[AclMetaOrRejection] =
    evaluate(path, AppendAcl(path, acl, rev, clock.instant(), identities))

  /**
    * Subtracts ''acl'' on a ''path''.
    *
    * @param path the target path for the ACL
    * @param acl  the identity to permissions mapping to subtract
    */
  def subtract(path: Path, rev: Long, acl: AccessControlList)(
      implicit identities: Set[Identity]): F[AclMetaOrRejection] =
    evaluate(path, SubtractAcl(path, acl, rev, clock.instant(), identities))

  /**
    * Delete all ACL on a ''path''.
    *
    * @param path the target path for the ACL
    */
  def delete(path: Path, rev: Long)(implicit identities: Set[Identity]): F[AclMetaOrRejection] =
    evaluate(path, DeleteAcl(path, rev, clock.instant(), identities))

  private def evaluate(path: Path, cmd: AclCommand): F[AclMetaOrRejection] =
    agg
      .evaluateS(path.repr, cmd)
      .map(_.flatMap {
        case Initial =>
          Left(AclUnexpected(path, "Unexpected initial state"))
        case Current(_, _, rev, created, updated, createdBy, updatedBy) =>
          Right(ResourceMetadata(base + path.repr, rev, types, createdBy, updatedBy, created, updated))
      })

  /**
    * Fetches the entire ACL for a ''path'' for all the identities on the latest revision
    *
    * @param path the target path for the ACL
    */
  def fetchUnsafe(path: Path): F[AccessControlList] =
    agg.currentState(path.repr).map(stateToAcl(path, _))

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
      .map(stateToAcl(path, _))

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

  private def stateToAcl(path: Path, state: AclState): AccessControlList =
    (state, path) match {
      case (Initial, initAcl.path) => initAcl.acl
      case (Initial, _)            => AccessControlList.empty
      case (c: Current, _)         => c.acl
    }
}
@SuppressWarnings(Array("OptionGet"))
object Acls {

  /**
    * Construct an ''Acls'' wrapped on an ''F'' type based on akka clustered [[Aggregate]].
    */
  def apply[F[_]: ConcurrentEffect](implicit cl: Clock = Clock.systemUTC(),
                                    ac: AppConfig,
                                    sc: AkkaSourcingConfig,
                                    as: ActorSystem,
                                    mt: ActorMaterializer): F[Acls[F]] = {
    val deferred = Deferred.unsafe[F, Acls[F]]

    val aggF = AkkaAggregate.sharded(
      "acls",
      AclState.Initial,
      next,
      evaluate(() => deferred.get),
      PassivationStrategy.immediately[AclState, AclCommand],
      RetryStrategy.never,
      sc,
      ac.cluster.shards
    )
    for {
      agg <- aggF
      acl = new Acls(agg)
      _ <- deferred.complete(acl)
    } yield acl
  }

  /**
    * Construct an ''Acls'' wrapped on an ''F'' type based on an in memory [[Aggregate]].
    */
  def inMemory[F[_]: ConcurrentEffect](implicit cl: Clock, initAcl: InitialAcl): F[Acls[F]] = {
    val deferred = Deferred.unsafe[F, Acls[F]]

    val aggF = Aggregate.inMemory[F, String]("acls", Initial, next, evaluate(() => deferred.get))
    for {
      agg <- aggF
      acl = new Acls(agg)
      _ <- deferred.complete(acl)
    } yield acl
  }

  /**
    * Aggregate type for ACLs.
    *
    * @tparam F the effect type under which the aggregate operates
    */
  type Agg[F[_]] = Aggregate[F, String, AclEvent, AclState, AclCommand, AclRejection]

  private[acls] val writePermission = Permission("acls/write").get

  private type EventOrRejection   = Either[AclRejection, AclEvent]
  private type AclMetaOrRejection = Either[AclRejection, ResourceMetadata]

  def next(state: AclState, ev: AclEvent): AclState = (state, ev) match {

    case (Initial, AclReplaced(p, acl, 1L, instant, identity)) =>
      Current(p, acl, 1L, instant, instant, identity, identity)

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

  def evaluate[F[_]: Async](f: () => F[Acls[F]])(state: AclState, command: AclCommand): F[EventOrRejection] = {
    val F = implicitly[Async[F]]

    def aclsF(): F[Acls[F]] = f()

    def hasWritePermissions(path: Path)(implicit identities: Set[Identity]): F[Boolean] = {
      val curr = for {
        acls <- aclsF()
        c    <- acls.fetch(path)
      } yield c
      curr.flatMap { c =>
        val hasPerms = c.value.exists {
          case (_, perms) => perms.contains(writePermission)
        }
        if (hasPerms) F.pure(true)
        else if (path.isEmpty) F.pure(false)
        else hasWritePermissions(path.tail)
      }
    }

    def permissionsCheck(cmd: AclCommand)(onSuccess: => AclEvent): F[EventOrRejection] =
      hasWritePermissions(cmd.path)(cmd.identities).map {
        case true  => Right(onSuccess)
        case false => Left(AclUnauthorizedWrite(cmd.path))
      }

    def replaced(c: ReplaceAcl): F[EventOrRejection] = (state, c.identities.subject) match {
      case (_, None)                           => F.pure(Left(AclMissingSubject))
      case _ if c.acl.hasVoidPermissions       => F.pure(Left(AclInvalidEmptyPermissions(c.path)))
      case (Initial, Some(s)) if c.rev == 0    => permissionsCheck(c)(AclReplaced(c.path, c.acl, 1L, c.instant, s))
      case (Initial, _)                        => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case (ss: Current, _) if c.rev != ss.rev => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case (_: Current, Some(s))               => permissionsCheck(c)(AclReplaced(c.path, c.acl, c.rev + 1, c.instant, s))
    }

    def append(c: AppendAcl): F[EventOrRejection] = (state, c.identities.subject) match {
      case (_, None)                                     => F.pure(Left(AclMissingSubject))
      case (Initial, _)                                  => F.pure(Left(AclNotFound(c.path)))
      case (ss: Current, _) if c.rev != ss.rev           => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case (_: Current, _) if c.acl.hasVoidPermissions   => F.pure(Left(AclInvalidEmptyPermissions(c.path)))
      case (ss: Current, _) if ss.acl ++ c.acl == ss.acl => F.pure(Left(NothingToBeUpdated(c.path)))
      case (_: Current, Some(s))                         => permissionsCheck(c)(AclAppended(c.path, c.acl, c.rev + 1, c.instant, s))
    }

    def subtract(c: SubtractAcl): F[EventOrRejection] = (state, c.identities.subject) match {
      case (Initial, _)                                  => F.pure(Left(AclNotFound(c.path)))
      case (ss: Current, _) if c.rev != ss.rev           => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case (_: Current, _) if c.acl.hasVoidPermissions   => F.pure(Left(AclInvalidEmptyPermissions(c.path)))
      case (ss: Current, _) if ss.acl -- c.acl == ss.acl => F.pure(Left(NothingToBeUpdated(c.path)))
      case (_: Current, Some(s))                         => permissionsCheck(c)(AclSubtracted(c.path, c.acl, c.rev + 1, c.instant, s))
    }

    def delete(c: DeleteAcl): F[EventOrRejection] = (state, c.identities.subject) match {
      case (Initial, _)                                          => F.pure(Left(AclNotFound(c.path)))
      case (ss: Current, _) if c.rev != ss.rev                   => F.pure(Left(AclIncorrectRev(c.path, c.rev)))
      case (ss: Current, _) if ss.acl == AccessControlList.empty => F.pure(Left(AclIsEmpty(c.path)))
      case (_: Current, Some(s))                                 => permissionsCheck(c)(AclDeleted(c.path, c.rev + 1, c.instant, s))
    }

    command match {
      case c: ReplaceAcl  => replaced(c)
      case c: AppendAcl   => append(c)
      case c: SubtractAcl => subtract(c)
      case c: DeleteAcl   => delete(c)
    }
  }
}
