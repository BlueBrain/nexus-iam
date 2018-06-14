package ch.epfl.bluebrain.nexus.iam.client.types

/**
  * Type definition that contains the definition of an access control
  *
  * @param identity    the identity for the ACLs
  * @param path        the path where the ACLs apply
  * @param permissions the permissions that are contained on the ACLs
  */
final case class FullAccessControl(identity: Identity, path: Address, permissions: Permissions)
