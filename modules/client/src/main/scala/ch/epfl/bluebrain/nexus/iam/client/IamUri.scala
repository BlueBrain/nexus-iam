package ch.epfl.bluebrain.nexus.iam.client

import akka.http.scaladsl.model.Uri

/**
  * Wrapper for IAM uri ''value''
  *
  * @param value the IAM uri
  */
final case class IamUri(value: Uri)
