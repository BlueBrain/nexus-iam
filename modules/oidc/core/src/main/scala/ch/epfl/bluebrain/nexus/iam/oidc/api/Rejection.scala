package ch.epfl.bluebrain.nexus.iam.oidc.api

/**
  * Top level type for rejections returned by the system.
  */
sealed trait Rejection extends Product with Serializable

object Rejection {

  /**
    * Rejection describing an invalid redirect uri format.
    */
  final case object IllegalRedirectUri extends Rejection

  /**
    * Rejection describing the intent to complete an server authorization flow with an invalid state.
    */
  final case object AuthorizationAttemptWithInvalidState extends Rejection

}
