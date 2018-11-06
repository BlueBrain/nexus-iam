package ch.epfl.bluebrain.nexus.iam.realms
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent._

sealed trait RealmState extends Product with Serializable

object RealmState {

  final case object Initial                                             extends RealmState
  final case class Current(realm: Realm, rev: Int, deprecated: Boolean) extends RealmState

  def next(current: RealmState, event: RealmEvent): RealmState = (current, event) match {
    case (Initial, RealmCreated(realm))               => Current(realm, rev = 1, deprecated = false)
    case (Current(_, _, _), RealmUpdated(realm, rev)) => Current(realm, rev, deprecated = false)
    case (Current(realm, _, _), RealmDeprecated(rev)) => Current(realm, rev, deprecated = true)
    case (other, _)                                   => other
  }
}
