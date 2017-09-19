package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.common.types.Err
import shapeless.Typeable

/**
  * Signals that a unexpected state was encountered upon evaluation of a particular command.
  *
  * @param expected the state that was expected
  * @param actual the actual state that was encountered
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
final case class UnexpectedState(expected: String, actual: String) extends Err("Discovered an unexpected state")

object UnexpectedState {

  /**
    * Convenience factory method to construct an [[ch.epfl.bluebrain.nexus.iam.core.acls.UnexpectedState]] instance.
    *
    * @param E a [[shapeless.Typeable]] instance for the expected state
    * @param A a [[shapeless.Typeable]] instance for the actual state
    * @tparam E the expected type
    * @tparam A the actual type
    * @return an [[ch.epfl.bluebrain.nexus.iam.core.acls.UnexpectedState]] instance
    */
  final def apply[E, A]()(implicit E: Typeable[E], A: Typeable[A]): UnexpectedState =
    UnexpectedState(E.describe, A.describe)
}
