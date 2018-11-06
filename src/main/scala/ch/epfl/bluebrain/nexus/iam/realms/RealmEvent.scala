package ch.epfl.bluebrain.nexus.iam.realms
import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity

/**
  * Enumeration of realm event types.
  */
sealed trait RealmEvent extends Product with Serializable {

  /**
    * @return the revision that this event generated
    */
  def rev: Long

  /**
    * @return the instant when this event was recorded
    */
  def instant: Instant

  /**
    * @return the identity which generated this event
    */
  def identity: Identity
}

object RealmEvent {

  /**
    * Event representing realm creation.
    *
    * @param realm    the representation of the realm
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class RealmCreated(realm: Realm, rev: Long, instant: Instant, identity: Identity) extends RealmEvent

  /**
    * Event representing realm update.
    *
    * @param realm    the updated of the realm
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class RealmUpdated(realm: Realm, rev: Long, instant: Instant, identity: Identity) extends RealmEvent

  /**
    * Event representing realm deprecation.
    *
    * @param rev      the revision that this event generated
    * @param instant  the instant when this event was recorded
    * @param identity the identity which generated this event
    */
  final case class RealmDeprecated(rev: Long, instant: Instant, identity: Identity) extends RealmEvent

}
