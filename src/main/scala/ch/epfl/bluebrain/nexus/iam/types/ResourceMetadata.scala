package ch.epfl.bluebrain.nexus.iam.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

object ResourceMetadata {

  def apply(
      id: AbsoluteIri,
      rev: Long,
      types: Set[AbsoluteIri],
      createdAt: Instant,
      createdBy: Identity,
      updatedAt: Instant,
      updatedBy: Identity
  ): ResourceMetadata =
    ResourceF.unit(id, rev, types, createdAt, createdBy, updatedAt, updatedBy)

}
