package ch.epfl.bluebrain.nexus.iam

import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.{Permission, ResourceF, ResourceMetadata}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.sourcing.Aggregate

package object acls {

  //TODO: replace this with publicUri / v1 / acls
  val base: Iri.AbsoluteIri = url"https://bluebrain.github.io/nexus/acls/".value

  val types: Set[AbsoluteIri] = Set(nxv.AccessControlList)

  val writePermission = Permission.unsafe("acls/write")

  type Agg[F[_]]                 = Aggregate[F, String, AclEvent, AclState, AclCommand, AclRejection]
  type EventOrRejection          = Either[AclRejection, AclEvent]
  type AclMetaOrRejection        = Either[AclRejection, ResourceMetadata]
  type ResourceAccessControlList = ResourceF[AccessControlList]

}
