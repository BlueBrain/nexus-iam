package ch.epfl.bluebrain.nexus.iam.types

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri

/**
  * The metadata information for any resource in the service
  *
  * @param id        the id of the resource
  * @param rev       the revision
  * @param types     the types of the resource
  * @param createdBy the identity that created the resource
  * @param updatedBy the identity that performed the last update to the resource
  * @param createdAt the creation date of the resource
  * @param updatedAt the last update date of the resource
  */
final case class ResourceMetadata(id: AbsoluteIri,
                                  rev: Long,
                                  types: Set[AbsoluteIri],
                                  createdBy: Identity,
                                  updatedBy: Identity,
                                  createdAt: Instant,
                                  updatedAt: Instant)
