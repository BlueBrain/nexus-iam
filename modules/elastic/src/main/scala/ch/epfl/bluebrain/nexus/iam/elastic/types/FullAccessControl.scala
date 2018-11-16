package ch.epfl.bluebrain.nexus.iam.elastic.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permissions
import ch.epfl.bluebrain.nexus.rdf.Iri.Path

/**
  * Type definition that contains the definition of an access control
  *
  * @param identity    the identity for the ACLs
  * @param path        the path where the ACLs apply
  * @param permissions the permissions that are contained on the ACLs
  */
final case class FullAccessControl(identity: Identity, path: Path, permissions: Permissions)
