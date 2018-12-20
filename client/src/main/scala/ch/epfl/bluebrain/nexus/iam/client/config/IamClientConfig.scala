package ch.epfl.bluebrain.nexus.iam.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

/**
  * Configuration for [[ch.epfl.bluebrain.nexus.iam.client.IamClient]].
  *
  * @param baseIri base URL for all the HTTP calls, including prefix.
  */
final case class IamClientConfig(baseIri: AbsoluteIri) {
  lazy val identitiesIri: AbsoluteIri = baseIri + "identities"
  lazy val aclsIri: AbsoluteIri       = baseIri + "acls"
}
