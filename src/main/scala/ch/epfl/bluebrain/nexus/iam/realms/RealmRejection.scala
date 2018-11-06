package ch.epfl.bluebrain.nexus.iam.realms

/**
  * Enumeration of realm rejection types.
  */
sealed trait RealmRejection extends Product with Serializable

object RealmRejection {

  /**
    * Rejection signalling that the realm does not exist.
    */
  final case object RealmDoesNotExistRejection extends RealmRejection

  /**
    * Rejection signalling that the realm already exists.
    */
  final case object RealmAlreadyExistsRejection extends RealmRejection

  /**
    * Rejection signalling that the realm is already deprecated.
    */
  final case object RealmDeprecatedRejection extends RealmRejection

  /**
    * Rejection signalling that the incorrect revision was supplied in the update.

    * @param currentRevision  the last known revision of the realm
    * @param updateRevision   the revision supplied in the update
    */
  final case class IncorrectRevisionRejection(currentRevision: Long, updateRevision: Long) extends RealmRejection
}
