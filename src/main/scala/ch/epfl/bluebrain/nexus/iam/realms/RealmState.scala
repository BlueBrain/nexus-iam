package ch.epfl.bluebrain.nexus.iam.realms
import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent._

/**
  * Enumeration of realm states.
  */
sealed trait RealmState extends Product with Serializable

object RealmState {

  /**
    * The initial (undefined) state.
    */
  final case object Initial extends RealmState

  /**
    * An existing realm state.
    *
    * @param realm      current representation of the realm
    * @param rev        current revision
    * @param deprecated whether the realm is deprecated or not
    * @param created    the instant when the realm was created
    * @param updated    the instant when the realm was last updated
    * @param createdBy  the identity that created the realm
    * @param updatedBy  the identity that last updated the realm
    */
  final case class Current(realm: Realm,
                           rev: Long,
                           deprecated: Boolean,
                           created: Instant,
                           updated: Instant,
                           createdBy: Identity,
                           updatedBy: Identity)
      extends RealmState

  /**
    * Function defining the state transition in response to an event.
    *
    * @param current  current state of the realm
    * @param event    event representing state change
    * @return         updated state
    */
  def next(current: RealmState, event: RealmEvent): RealmState = (current, event) match {
    case (Initial, RealmCreated(realm, rev, instant, identity)) =>
      Current(realm,
              rev,
              deprecated = false,
              createdBy = identity,
              updatedBy = identity,
              created = instant,
              updated = instant)

    case (current: Current, RealmUpdated(realm, rev, instant, identity)) =>
      current.copy(realm = realm, rev = rev, updatedBy = identity, updated = instant)

    case (current: Current, RealmDeprecated(rev, instant, identity)) =>
      current.copy(rev = rev, deprecated = true, updated = instant, updatedBy = identity)

    case (other, _) => other
  }
}
