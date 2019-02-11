package ch.epfl.bluebrain.nexus.iam.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.iam.client.IamClient]].
  *
  * @param publicIri           base URL for all the identity IDs, including prefix.
  * @param internalIri         base URL for all the HTTP calls, including prefix.
  * @param sseRetryDelay       delay for retrying after completion on SSE. 1 second by default.
  */
final case class IamClientConfig(publicIri: AbsoluteIri,
                                 internalIri: AbsoluteIri,
                                 sseRetryDelay: FiniteDuration = 1 second) {
  lazy val identitiesIri: AbsoluteIri  = internalIri + "identities"
  lazy val aclsIri: AbsoluteIri        = internalIri + "acls"
  lazy val permissionsIri: AbsoluteIri = internalIri + "permissions"
  lazy val realmsIri: AbsoluteIri      = internalIri + "realms"
}
