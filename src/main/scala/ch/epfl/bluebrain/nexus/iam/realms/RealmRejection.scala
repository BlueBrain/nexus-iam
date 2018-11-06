package ch.epfl.bluebrain.nexus.iam.realms

sealed trait RealmRejection extends Product with Serializable

object RealmRejection {
  final case object RealmDoesNotExist                                           extends RealmRejection
  final case object RealmAlreadyExists                                          extends RealmRejection
  final case object RealmDeprecated                                             extends RealmRejection
  final case class IncorrectRevision(currentRevision: Int, updateRevision: Int) extends RealmRejection
}
