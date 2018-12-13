package ch.epfl.bluebrain.nexus.iam.client.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

final case class IamClientConfig(prefix: String, publicIri: AbsoluteIri)
