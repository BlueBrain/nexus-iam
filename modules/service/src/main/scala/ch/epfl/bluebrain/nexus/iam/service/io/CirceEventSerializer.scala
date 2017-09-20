package ch.epfl.bluebrain.nexus.iam.service.io

import java.nio.ByteBuffer

import akka.serialization.SerializerWithStringManifest
import ch.epfl.bluebrain.nexus.iam.core.acls.{Event, UnableToDeserializeEvent}
import ch.epfl.bluebrain.nexus.iam.service.io.CirceEventSerializer.EventMF
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSerialization._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.java8.time._

/**
  * Custom serializer that uses Circe to encode and decode ''ACL'' domain [[ch.epfl.bluebrain.nexus.iam.core.acls.Event]]s
  * in a json format.
  */
class CirceEventSerializer extends SerializerWithStringManifest {

  override val identifier: Int = ByteBuffer.wrap("circe".getBytes(UTF8)).getInt

  override def manifest(o: AnyRef): String = o match {
    case _: Event => EventMF
    case _        => throw new IllegalArgumentException(s"Unknown manifest for '${o.getClass.getCanonicalName}'")
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: Event => aToByteArray(e)
    case _        => throw new IllegalArgumentException(s"Unable to serialize '${o.getClass.getCanonicalName}'")
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef =
    manifest match {
      case EventMF => decode[Event](new String(bytes, UTF8)).right.getOrElse(throw UnableToDeserializeEvent)
      case _       => throw new IllegalArgumentException(s"Unsupported manifest '$manifest'")
    }
}

object CirceEventSerializer {

  /**
    * Constant manifest for the ''ACL'' domain.  The serializer uses an internal discriminator to differentiate between
    * various [[ch.epfl.bluebrain.nexus.iam.core.acls.Event]]s.
    */
  val EventMF = "event"
}
