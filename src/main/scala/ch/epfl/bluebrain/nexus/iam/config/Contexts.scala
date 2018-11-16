package ch.epfl.bluebrain.nexus.iam.config

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._

object Contexts {
  val base = url"https://bluebrain.github.io/nexus/contexts/".value

  val resourceCtxUri: AbsoluteIri = base + "resource.json"
  val iamCtxUri: AbsoluteIri      = base + "iam.json"
  val searchCtxUri: AbsoluteIri   = base + "search.json"
  val errorCtxUri: AbsoluteIri    = base + "error.json"
}
