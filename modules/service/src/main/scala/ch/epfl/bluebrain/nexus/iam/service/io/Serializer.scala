package ch.epfl.bluebrain.nexus.iam.service.io

import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import ch.epfl.bluebrain.nexus.iam.core.groups.GroupPermissionAddedEvent
import ch.epfl.bluebrain.nexus.service.serialization.AkkaCoproductSerializer
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.java8.time._
import shapeless._

/**
  * Akka ''SerializerWithStringManifest'' class definition for all events.
  * The serializer provides the types available for serialization.
  */
object Serializer {

  implicit val config: Configuration = Configuration.default.withDiscriminator("type")

  class EventSerializer                     extends AkkaCoproductSerializer[Event :+: CNil](1215)
  class GroupPermissionAddedEventSerializer extends AkkaCoproductSerializer[GroupPermissionAddedEvent :+: CNil](1216)

}
