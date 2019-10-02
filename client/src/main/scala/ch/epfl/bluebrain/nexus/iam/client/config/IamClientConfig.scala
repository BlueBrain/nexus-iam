package ch.epfl.bluebrain.nexus.iam.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.iam.client.IamClient]].
  *
  * @param publicIri     base URL for all the identity IDs, excluding prefix.
  * @param internalIri   base URL for all the HTTP calls, excluding prefix.
  * @param prefix        the prefix
  * @param sseRetryDelay delay for retrying after completion on SSE. 1 second by default.
  */
final case class IamClientConfig(
    publicIri: AbsoluteIri,
    internalIri: AbsoluteIri,
    prefix: String,
    sseRetryDelay: FiniteDuration = 1 second
) {
  lazy val baseInternalIri             = internalIri + prefix
  lazy val basePublicIri               = publicIri + prefix
  lazy val identitiesIri: AbsoluteIri  = baseInternalIri + "identities"
  lazy val aclsIri: AbsoluteIri        = baseInternalIri + "acls"
  lazy val permissionsIri: AbsoluteIri = baseInternalIri + "permissions"
  lazy val realmsIri: AbsoluteIri      = baseInternalIri + "realms"
  lazy val eventsIri: AbsoluteIri      = baseInternalIri + "events"
}
