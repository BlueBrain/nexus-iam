package ch.epfl.bluebrain.nexus.iam.client.types

/**
  * Class representing available permissions response.
  *
  * @param permissions available permissions
  */
final case class Permissions(permissions: Set[Permission])
