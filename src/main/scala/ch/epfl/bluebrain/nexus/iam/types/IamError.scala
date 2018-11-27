package ch.epfl.bluebrain.nexus.iam.types
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

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
}
