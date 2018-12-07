package ch.epfl.bluebrain.nexus.iam.realms

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.Monad
import cats.effect._
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.iam.acls.Acls
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{HttpConfig, RealmsConfig}
import ch.epfl.bluebrain.nexus.iam.index.KeyValueStore
import ch.epfl.bluebrain.nexus.iam.realms.RealmCommand.{CreateRealm, DeprecateRealm, UpdateRealm}
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent.{RealmCreated, RealmDeprecated, RealmUpdated}
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.RealmState.{Active, Current, Deprecated, Initial}
import ch.epfl.bluebrain.nexus.iam.realms.Realms.next
import ch.epfl.bluebrain.nexus.iam.types.IamError.{AccessDenied, UnexpectedInitialState}
import ch.epfl.bluebrain.nexus.iam.types._
import ch.epfl.bluebrain.nexus.rdf.Iri.{Path, Url}
import ch.epfl.bluebrain.nexus.sourcing.akka.AkkaAggregate
import io.circe.Json

/**
  * Realms API.
  *
  * @param agg   the realms aggregate
  * @param acls  a lazy acls api
  * @param index an index implementation for realms
  * @param http  the application http configurations
  * @tparam F    the effect type
  */
class Realms[F[_]: MonadThrowable](agg: Agg[F], acls: Lazy[F, Acls], index: RealmIndex[F])(implicit http: HttpConfig) {

  private val F = implicitly[MonadThrowable[F]]

  /**
    * Creates a new realm using the provided configuration.
    *
    * @param id           the realm id
    * @param name         the name of the realm
    * @param openIdConfig the address of the openid configuration
    * @param logo         an optional realm logo
    */
  def create(
      id: Label,
      name: String,
      openIdConfig: Url,
      logo: Option[Url]
  )(implicit caller: Caller): F[MetaOrRejection] =
    check(write) *> eval(CreateRealm(id, name, openIdConfig, logo, caller.subject)) <* updateIndex(id)

  /**
    * Updates an existing realm using the provided configuration.
    *
    * @param id           the realm id
    * @param rev          the current revision of the realm
    * @param name         an optional new name for the realm
    * @param openIdConfig an optional new openid configuration address
    * @param logo         an optional new logo
    */
  def update(
      id: Label,
      rev: Long,
      name: Option[String],
      openIdConfig: Option[Url],
      logo: Option[Url]
  )(implicit caller: Caller): F[MetaOrRejection] =
    check(id, write) *> eval(UpdateRealm(id, rev, name, openIdConfig, logo, caller.subject)) <* updateIndex(id)

  /**
    * Deprecates an existing realm. A deprecated realm prevents clients from authenticating.
    *
    * @param id  the id of the realm
    * @param rev the revision of the realm
    */
  def deprecate(id: Label, rev: Long)(implicit caller: Caller): F[MetaOrRejection] =
    check(id, write) *> eval(DeprecateRealm(id, rev, caller.subject)) <* updateIndex(id)

  /**
    * Fetches a realm.
    *
    * @param id the id of the realm
    * @return the realm in a Resource representation, None otherwise
    */
  def fetch(id: Label)(implicit caller: Caller): F[OptResource] =
    check(id, read) *> fetchUnsafe(id)

  /**
    * Fetches a realm at a specific revision.
    *
    * @param id  the id of the realm
    * @param rev the revision of the realm
    * @return the realm in a Resource representation, None otherwise
    */
  def fetch(id: Label, rev: Long)(implicit caller: Caller): F[OptResource] =
    check(id, read) *> fetchUnsafe(id, Some(rev))

  /**
    * @return the current realms sorted by their creation date.
    */
  def list(implicit caller: Caller): F[List[Resource]] =
    check(read) *> index.values().map(set => set.toList.sortBy(_.createdAt.toEpochMilli))

  /**
    * Attempts to compute the token from the given [[AuthToken]]
    *
    * @param token the provided token
    * @return Some(caller) when the token is valid and a [[Caller]] can be linked to it,
    *         None otherwise. The result is wrapped on an ''F'' effect type.
    */
  def caller(token: AuthToken): F[Option[Caller]] = ???

  private def fetchUnsafe(id: Label, optRev: Option[Long] = None): F[OptResource] =
    stateOf(id, optRev).map(_.optResource)

  private def check(id: Label, permission: Permission)(implicit caller: Caller): F[Unit] =
    acls()
      .flatMap(_.hasPermission(id.toPath, permission))
      .ifM(F.unit, F.raiseError(AccessDenied(id.toIri(http.realmsIri), permission)))

  private def check(permission: Permission)(implicit caller: Caller): F[Unit] =
    acls()
      .flatMap(_.hasPermission(Path./, permission, ancestors = false))
      .ifM(F.unit, F.raiseError(AccessDenied(http.realmsIri, permission)))

  private def eval(cmd: Command): F[MetaOrRejection] =
    agg
      .evaluateS(cmd.id.value, cmd)
      .flatMap {
        case Left(rej) => F.pure(Left(rej))
        // $COVERAGE-OFF$
        case Right(Initial) => F.raiseError(UnexpectedInitialState(cmd.id.toIri(http.realmsIri)))
        // $COVERAGE-ON$
        case Right(c: Current) => F.pure(Right(c.resourceMetadata))
      }

  private def stateOf(id: Label, optRev: Option[Long]): F[State] =
    optRev
      .map { rev =>
        agg.foldLeft[State](id.value, Initial) {
          case (state, event) if event.rev <= rev => next(state, event)
          case (state, _)                         => state
        }
      }
      .getOrElse(agg.currentState(id.value))

  private def updateIndex(id: Label): F[Unit] =
    fetchUnsafe(id).flatMap {
      case Some(res) => index.put(id, res)
      case None      => F.unit
    }
}

object Realms {

  /**
    * Creates a new realm index.
    */
  def index[F[_]: Async: Timer](implicit as: ActorSystem, rc: RealmsConfig): RealmIndex[F] =
    KeyValueStore.distributed(
      "realms",
      (_, resource) => resource.rev,
      rc.keyValueStore.askTimeout,
      rc.keyValueStore.consistencyTimeout,
      rc.keyValueStore.retry.retryStrategy
    )

  /**
    * Constructs a new realms aggregate.
    */
  def aggregate[F[_]: Effect: Timer: Clock](
      implicit as: ActorSystem,
      mt: ActorMaterializer,
      rc: RealmsConfig,
      hc: HttpClient[F, Json]
  ): F[Agg[F]] =
    AkkaAggregate.sharded[F](
      "realms",
      RealmState.Initial,
      next,
      evaluate[F],
      rc.sourcing.passivationStrategy(),
      rc.sourcing.retry.retryStrategy,
      rc.sourcing.akkaSourcingConfig,
      rc.sourcing.shards
    )

  /**
    * Creates a new realms api using the provided aggregate, a lazy reference to the ACL api and a realm index reference.
    *
    * @param agg   the permissions aggregate
    * @param acls  a lazy reference to the ACL api
    * @param index a realm index reference
    */
  def apply[F[_]: MonadThrowable](
      agg: Agg[F],
      acls: Lazy[F, Acls],
      index: RealmIndex[F]
  )(implicit http: HttpConfig): Realms[F] =
    new Realms(agg, acls, index)

  /**
    * Creates a new permissions api using the default aggregate and a lazy reference to the ACL api.
    *
    * @param acls a lazy reference to the ACL api
    */
  def apply[F[_]: Effect: Timer: Clock](acls: Lazy[F, Acls])(
      implicit
      as: ActorSystem,
      mt: ActorMaterializer,
      http: HttpConfig,
      hc: HttpClient[F, Json],
      rc: RealmsConfig
  ): F[Realms[F]] =
    delay(aggregate, acls, index)

  /**
    * Creates a new realms api using the provided aggregate, a lazy reference to the ACL api and a realm index.
    *
    * @param agg  a lazy reference to the permissions aggregate
    * @param acls a lazy reference to the ACL api
    * @param index a realm index reference
    */
  def delay[F[_]: MonadThrowable](
      agg: F[Agg[F]],
      acls: Lazy[F, Acls],
      index: RealmIndex[F]
  )(implicit http: HttpConfig): F[Realms[F]] =
    agg.map(apply(_, acls, index))

  private[realms] def next(state: State, event: Event): State = {
    // format: off
    def created(e: RealmCreated): State = state match {
      case Initial => Active(e.id, e.rev, e.name, e.openIdConfig, e.issuer, e.keys, e.grantTypes, e.logo, e.instant, e.subject, e.instant, e.subject)
      case other   => other
    }
    def updated(e: RealmUpdated): State = state match {
      case s: Current => Active(e.id, e.rev, e.name, e.openIdConfig, e.issuer, e.keys, e.grantTypes, e.logo, s.createdAt, s.createdBy, e.instant, e.subject)
      case other      => other
    }
    def deprecated(e: RealmDeprecated): State = state match {
      case s: Active => Deprecated(e.id, e.rev, s.name, s.openIdConfig, s.logo, s.createdAt, s.createdBy, e.instant, e.subject)
      case other     => other
    }
    // format: on
    event match {
      case e: RealmCreated    => created(e)
      case e: RealmUpdated    => updated(e)
      case e: RealmDeprecated => deprecated(e)
    }
  }

  private def evaluate[F[_]: MonadThrowable: Clock: HttpJsonClient](state: State, cmd: Command): F[EventOrRejection] = {
    val F = implicitly[Monad[F]]
    val C = implicitly[Clock[F]]
    def instantF: F[Instant] =
      C.realTime(TimeUnit.MILLISECONDS).map(Instant.ofEpochMilli)
    def accept(f: Instant => Event): F[EventOrRejection] =
      instantF.map { instant =>
        Right(f(instant))
      }
    def reject(rejection: Rejection): F[EventOrRejection] =
      F.pure(Left(rejection))
    // format: off
    def create(c: CreateRealm): F[EventOrRejection] = state match {
      case Initial =>
        for {
          instant  <- instantF
          wkeither <- WellKnown[F](c.openIdConfig)
        } yield wkeither.map { wk =>
          RealmCreated(c.id, 1L, c.name, c.openIdConfig, wk.issuer, wk.keys, wk.grantTypes, c.logo, instant, c.subject)
        }
      case _ => reject(RealmAlreadyExists(c.id))
    }
    def update(c: UpdateRealm): F[EventOrRejection] = state match {
      case Initial                      => reject(RealmNotFound(c.id))
      case s: Current if s.rev != c.rev => reject(IncorrectRev(c.rev))
      case s: Current =>
        val cfg  = c.openIdConfig.getOrElse(s.openIdConfig)
        val name = c.name.getOrElse(s.name)
        val logo = c.logo orElse s.logo
        for {
          instant  <- instantF
          wkeither <- WellKnown[F](cfg)
        } yield wkeither.map { wk =>
          RealmUpdated(c.id, s.rev + 1, name, cfg, wk.issuer, wk.keys, wk.grantTypes, logo, instant, c.subject)
        }
    }
    def deprecate(c: DeprecateRealm): F[EventOrRejection] = state match {
      case Initial                      => reject(RealmNotFound(c.id))
      case s: Current if s.rev != c.rev => reject(IncorrectRev(c.rev))
      case _: Deprecated                => reject(RealmAlreadyDeprecated(c.id))
      case s: Current                   => accept(RealmDeprecated(s.id, s.rev + 1, _, c.subject))
    }
    // format: on

    cmd match {
      case c: CreateRealm    => create(c)
      case c: UpdateRealm    => update(c)
      case c: DeprecateRealm => deprecate(c)
    }
  }
}
