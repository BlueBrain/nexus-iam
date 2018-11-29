package ch.epfl.bluebrain.nexus.iam.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.iam.types.Identity.Subject
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

object ResourceMetadata {

  /**
    * Constructs a [[ResourceF]] where the value is of type Unit
    *
    * @param id         the identifier of the resource
    * @param rev        the revision of the resource
    * @param types      the types of the resource
    * @param deprecated the resource deprecation status
    * @param createdAt  the instant when the resource was created
    * @param createdBy  the subject that created the resource
    * @param updatedAt  the instant when the resource was updated
    * @param updatedBy  the subject that updated the resource
    */
  def apply(
      id: AbsoluteIri,
      rev: Long,
      types: Set[AbsoluteIri],
      deprecated: Boolean,
      createdAt: Instant,
      createdBy: Subject,
      updatedAt: Instant,
      updatedBy: Subject
  ): ResourceMetadata =
    ResourceF.unit(id, rev, types, deprecated, createdAt, createdBy, updatedAt, updatedBy)

}
