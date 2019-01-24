package ch.epfl.bluebrain.nexus.iam.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.iam.client.IamClient]].
  *
  * @param publicIri    base URL for all the identity IDs, including prefix.
  * @param internalIri  base URL for all the HTTP calls, including prefix.
  */
final case class IamClientConfig(publicIri: AbsoluteIri, internalIri: AbsoluteIri) {
  lazy val identitiesIri: AbsoluteIri  = internalIri + "identities"
  lazy val aclsIri: AbsoluteIri        = internalIri + "acls"
  lazy val permissionsIri: AbsoluteIri = internalIri + "permissions"
}
