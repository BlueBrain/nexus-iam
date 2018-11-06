package ch.epfl.bluebrain.nexus.iam.realms
import cats.effect.Async
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent._
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.RealmState._

sealed trait RealmCommand extends Product with Serializable

object RealmCommand {

  final case class CreateRealm(realm: Realm)           extends RealmCommand
  final case class UpdateRealm(realm: Realm, rev: Int) extends RealmCommand
  final case class DeprecateRealm(rev: Int)            extends RealmCommand

  def evaluate[F[_]](state: RealmState, command: RealmCommand)(
      implicit F: Async[F]): F[Either[RealmRejection, RealmEvent]] = (state, command) match {
    case (Initial, CreateRealm(realm))      => F.pure(Right(RealmCreated(realm)))
    case (Initial, _)                       => F.pure(Left(RealmDoesNotExist))
    case (Current(_, _, _), CreateRealm(_)) => F.pure(Left(RealmAlreadyExists))
    case (Current(_, currentRev, false), UpdateRealm(realm, updateRev)) if currentRev == updateRev =>
      F.pure(Right(RealmUpdated(realm, currentRev + 1)))
    case (Current(_, currentRev, false), UpdateRealm(_, updateRev)) =>
      F.pure(Left(IncorrectRevision(currentRev, updateRev)))
    case (Current(_, _, true), _) => F.pure(Left(RealmRejection.RealmDeprecated))
    case (Current(_, currentRev, false), DeprecateRealm(updateRev)) if currentRev == updateRev =>
      F.pure(Right(RealmDeprecated(currentRev + 1)))
    case (Current(_, currentRev, false), DeprecateRealm(updateRev)) =>
      F.pure(Left(IncorrectRevision(currentRev, updateRev)))
    case (Current(_, currentRev, false), DeprecateRealm(updateRev)) =>
      F.pure(Left(IncorrectRevision(currentRev, updateRev)))
  }
}
