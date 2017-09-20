package ch.epfl.bluebrain.nexus.iam.service.types

/**
  * A service description.
  *
  * @param name    the name of the service
  * @param version the current version of the service
  */
final case class ServiceDescription(name: String, version: String)
