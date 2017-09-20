package ch.epfl.bluebrain.nexus.iam.service.types

/**
  * A self descriptive resource address.
  *
  * @param rel  the link relationship with the current resource
  * @param href the address to the resource
  */
final case class Link(rel: String, href: String)
