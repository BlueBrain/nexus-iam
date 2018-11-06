package ch.epfl.bluebrain.nexus.iam.realms

sealed trait RealmEvent extends Product with Serializable

object RealmEvent {

  final case class RealmCreated(realm: Realm)           extends RealmEvent
  final case class RealmUpdated(realm: Realm, rev: Int) extends RealmEvent
  final case class RealmDeprecated(rev: Int)            extends RealmEvent

}
