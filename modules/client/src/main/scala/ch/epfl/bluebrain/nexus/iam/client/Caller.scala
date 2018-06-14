package ch.epfl.bluebrain.nexus.iam.client

import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.iam.client.types.{AuthToken, Identity}

/**
  * Base enumeration type for caller classes.
  */
sealed trait Caller extends Product with Serializable {

  /**
    * @return the identities this ''caller'' belongs to
    */
  def identities: Set[Identity]

  /**
    * @return the ''credentials'' used by the caller to authenticate
    */
  def credentials: Option[AuthToken]
}
object Caller {

  /**
    * An anonymous caller.
    *
    */
  final case object AnonymousCaller extends Caller {
    override val identities  = Set(Anonymous)
    override val credentials = None
  }

  /**
    * An authenticated caller.
    *
    * @param token the identities this ''caller'' belongs to
    * @param self the [[UserRef]] of the caller
    * @param identities the ''credentials'' used by the caller to authenticate
    */
  final case class AuthenticatedCaller(token: AuthToken, self: UserRef, identities: Set[Identity]) extends Caller {
    def credentials: Option[AuthToken] = Some(token)
  }

  /**
    * Implicit conversion from implicitly available ''available'' to optional [[AuthToken]]
    *
    * @param caller the implicitly available caller
    * @return an optional [[AuthToken]]
    */
  final implicit def callerToToken(implicit caller: Caller): Option[AuthToken] =
    caller.credentials
}
