package ch.epfl.bluebrain.nexus.iam.types

import ch.epfl.bluebrain.nexus.iam.auth.TokenRejection
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

import scala.concurrent.duration.FiniteDuration

/**
  * Generic error types global to the entire service.
  *
  * @param msg the reason why the error occurred
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class IamError(msg: String) extends Exception with Product with Serializable {
  override def fillInStackTrace(): Throwable = this
  override def getMessage: String            = msg
}

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object IamError {

  /**
    * Signals the failure to perform an action on a resource, because of lacking permission.
    *
    * @param resource   the resource on which the action was attempted
    * @param permission the missing permission
    */
  final case class AccessDenied(resource: AbsoluteIri, permission: Permission)
      extends IamError(s"Access '${permission.value}' to resource '${resource.asUri}' denied.")

  /**
    * Signals an unexpected state was detected after a command evaluation.
    *
    * @param resource the resource on which the action was attempted
    */
  final case class UnexpectedInitialState(resource: AbsoluteIri)
      extends IamError(s"Unexpected state on resource '${resource.asUri}'.")

  /**
    * Signals that a timeout occurred while waiting for the desired read or write consistency across nodes.
    *
    * @param timeout the timeout duration
    */
  final case class ReadWriteConsistencyTimeout(timeout: FiniteDuration)
      extends IamError(s"Timed out after '${timeout.toMillis} ms' while waiting for a consistent read or write.")

  /**
    * Signals that an error occurred when trying to perform a distributed data operation.
    */
  final case class DistributedDataError(reason: String)
      extends IamError(s"An error occurred when performing a distributed data operation, reason '$reason'.")

  /**
    * Generic wrapper for iam errors that should not be exposed to clients.
    *
    * @param error the underlying error
    */
  final case class InternalError(error: IamError) extends IamError("An internal server error occurred.")

  /**
    * Signals that an error occurred while attempting to perform an operation with an invalid access token.
    *
    * @param reason a reason for why the token is considered invalid
    */
  final case class InvalidAccessToken(reason: TokenRejection) extends IamError("The provided access token is invalid.")

}
