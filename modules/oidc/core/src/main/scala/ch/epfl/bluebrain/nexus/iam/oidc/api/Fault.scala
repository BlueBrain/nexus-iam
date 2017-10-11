package ch.epfl.bluebrain.nexus.iam.oidc.api

import ch.epfl.bluebrain.nexus.commons.types.Err

/**
  * Top level error type that does not fill in the stack trace when thrown.  It also enforces the presence of a message.
  *
  * @param reason a text describing the reason as to why this exception has been raised
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class Fault(reason: String) extends Err(reason)

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object Fault {

  /**
    * Signals that an operation was rejected due to the inappropriate moment or arguments used.
    *
    * @param rejection the rejection result
    */
  final case class Rejected(rejection: Rejection) extends Fault("Intent was rejected")

  /**
    * Signals that an operation has timed out.
    */
  final case class TimedOut(reason: String) extends Fault(reason)

  /**
    * Signals that an unexpected reply was received during internal actor messaging.
    *
    * @param reason a text describing the reason as to why this exception has been raised
    * @param msg the message received
    */
  final case class Unexpected(reason: String, msg: Any) extends Fault(reason)

  /**
    * Signals a failure when communicating with a downstream service.
    *
    * @param reason a text describing the reason as to why this exception has been raised
    * @param cause the underlying cause of the failure
    */
  final case class UnsuccessfulDownstreamCall(reason: String, cause: Throwable) extends Fault(reason)

  /**
    * Signals a generic failure in the system.
    *
    * @param reason a text describing the reason as to why this exception has been raised
    * @param cause the underlying cause of the failure
    */
  final case class InternalFault(reason: String, cause: Throwable) extends Fault(reason)

  /**
    * Signals the lack of authorization to perform an operation.
    */
  final case object Unauthorized extends Fault("The caller is not permitted to perform this request")

}
