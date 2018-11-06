package ch.epfl.bluebrain.nexus.iam.realms
import java.time.Instant

import cats.effect.Async
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent._
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.RealmState._

/**
  * Enumeration of the realm command types
  */
sealed trait RealmCommand extends Product with Serializable {

  /**
    * @return the last known revision of the realm when this command was created
    */
  def rev: Long

  /**
    * @return the instant when this command was created
    */
  def instant: Instant

  /**
    * @return the identity which created this command
    */
  def identity: Identity
}

object RealmCommand {

  /**
    * An intent for realm creation.
    *
    * @param realm    new realm representation
    * @param rev      the last known revision of the realm when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class CreateRealm(realm: Realm, rev: Long, instant: Instant, identity: Identity) extends RealmCommand

  /**
    * An intent for realm update.
    *
    * @param realm    updated realm representation
    * @param rev      the last known revision of the realm when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class UpdateRealm(realm: Realm, rev: Long, instant: Instant, identity: Identity) extends RealmCommand

  /**
    * An intent for realm deprecation.
    *
    * @param rev      the last known revision of the realm when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class DeprecateRealm(rev: Long, instant: Instant, identity: Identity) extends RealmCommand

  /**
    * Evaluate a command and based on the current state of the realm produce `Either[RealmRejection, RealmEvent]`
    * in context of [[[F]]].
    *
    * @param state    the current state of the realm
    * @param command  the command to evaluate
    * @param F        the context in which to return the result
    * @return         the event resulting from the application of the command to the current state or a rejection
    */
  def evaluate[F[_]](state: RealmState, command: RealmCommand)(
      implicit F: Async[F]): F[Either[RealmRejection, RealmEvent]] = {

    def create(create: CreateRealm)(implicit F: Async[F]): F[Either[RealmRejection, RealmEvent]] = state match {
      case Initial    => F.pure(Right(RealmCreated(create.realm, create.rev, create.instant, create.identity)))
      case _: Current => F.pure(Left(RealmAlreadyExistsRejection))
    }

    def update(update: UpdateRealm)(implicit F: Async[F]): F[Either[RealmRejection, RealmEvent]] = state match {
      case Current(_, update.rev, false, _, _, _, _) =>
        F.pure(Right(RealmUpdated(update.realm, update.rev + 1, update.instant, update.identity)))
      case Current(_, _, true, _, _, _, _) =>
        F.pure(Left(RealmDeprecatedRejection))
      case Current(_, currentRev, _, _, _, _, _) =>
        F.pure(Left(IncorrectRevisionRejection(currentRev, update.rev)))
      case Initial => F.pure(Left(RealmDoesNotExistRejection))
    }

    def deprecate(deprecate: DeprecateRealm)(implicit F: Async[F]): F[Either[RealmRejection, RealmEvent]] =
      state match {
        case Current(_, deprecate.rev, false, _, _, _, _) =>
          F.pure(Right(RealmDeprecated(deprecate.rev + 1, deprecate.instant, deprecate.identity)))
        case Current(_, _, true, _, _, _, _) =>
          F.pure(Left(RealmDeprecatedRejection))
        case Current(_, currentRev, _, _, _, _, _) =>
          F.pure(Left(IncorrectRevisionRejection(currentRev, deprecate.rev)))
        case Initial => F.pure(Left(RealmDoesNotExistRejection))
      }

    command match {
      case c: CreateRealm    => create(c)
      case u: UpdateRealm    => update(u)
      case d: DeprecateRealm => deprecate(d)
    }
  }
}
