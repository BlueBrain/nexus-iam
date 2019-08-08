package ch.epfl.bluebrain.nexus.iam.client
import akka.http.scaladsl.model.StatusCode

import scala.reflect.ClassTag

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
sealed abstract class IamClientError(val message: String) extends Exception {
  override def fillInStackTrace(): IamClientError = this
  override val getMessage: String                 = message
}

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
object IamClientError {

  final case class Unauthorized(entityAsString: String)
      extends IamClientError("The request did not complete successfully due to an invalid authentication method.")

  final case class Forbidden(entityAsString: String)
      extends IamClientError("The request did not complete successfully due to lack of access to the resource.")

  final case class UnmarshallingError[A: ClassTag](reason: String)
      extends IamClientError(
        s"Unable to parse or decode the response from IAM to a '${implicitly[ClassTag[A]]}' due to '$reason'."
      )

  final case class UnknownError(status: StatusCode, entityAsString: String)
      extends IamClientError("The request did not complete successfully.")
}
