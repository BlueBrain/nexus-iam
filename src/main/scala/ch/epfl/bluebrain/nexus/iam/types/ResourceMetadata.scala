package ch.epfl.bluebrain.nexus.iam.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

object ResourceMetadata {

  /**
    * Constrcuts a [[ResourceF]] where the value is of type Unit
    *
    * @param id        the identifier of the resource
    * @param rev       the revision of the resource
    * @param types     the types of the resource
    * @param createdAt the instant when the resource was created
    * @param createdBy the identity that created the resource
    * @param updatedAt the instant when the resource was updated
    * @param updatedBy the identity that updated the resource
    */
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
