package ch.epfl.bluebrain.nexus.iam.service.types

import akka.http.scaladsl.model.Uri

/**
  * Encodes the intent of the uri value in a well defined type.
  *
  * @param base the underlying base uri
  */
final case class ApiUri(base: Uri)
