package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.iam.types.Label

/**
  * Enumeration of realm rejection types.
  *
  * @param msg a descriptive message for why the rejection occurred
  */
sealed abstract class RealmRejection(val msg: String) extends Product with Serializable

object RealmRejection {

  /**
    * Rejection returned when attempting to create a realm with an id that already exists
    *
    * @param id the id of the realm
    */
  final case class RealmAlreadyExists(id: Label) extends RealmRejection(s"Realm '${id.value}' already exists.")

  /**
    * Rejection returned when a subject intends to perform an operation on the current realm, but either provided an
    * incorrect revision or a concurrent update won over this attempt.
    *
    * @param rev the provided revision
    */
  final case class IncorrectRev(rev: Long)
      extends RealmRejection(s"Incorrect revision '$rev' provided, the realm may have been updated since last seen.")
}
