package ch.epfl.bluebrain.nexus.iam.io

import java.time.Instant

import akka.actor.ExtendedActorSystem
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent.AclDeleted
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsEvent.PermissionsDeleted
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import io.circe.parser._
import org.scalatest.{EitherValues, Inspectors, Matchers}

class EventSerializerSpec extends ActorSystemFixture("SerializerSpec") with Matchers with Inspectors with EitherValues {

  private val pd = PermissionsDeleted(2L, Instant.EPOCH, Anonymous)
  private val pdString =
    """|{
       |  "rev": 2,
       |  "instant": "1970-01-01T00:00:00Z",
       |  "subject": {
       |    "@id": "http://127.0.0.1:8080/v1/anonymous",
       |    "@type": "Anonymous"
       |  },
       |  "@type": "PermissionsDeleted"
       |}""".stripMargin

  private val ad = AclDeleted(Path("/a/b/c").right.value, 2L, Instant.EPOCH, Anonymous)
  private val adString =
    """|{
       |  "path": "/a/b/c",
       |  "rev": 2,
       |  "instant": "1970-01-01T00:00:00Z",
       |  "subject": {
       |    "@id": "http://127.0.0.1:8080/v1/anonymous",
       |    "@type": "Anonymous"
       |  },
       |  "@type": "AclDeleted"
       |}""".stripMargin

  private val data = Map[AnyRef, (String, String)](
    pd -> ("permissions-event" -> pdString),
    ad -> ("acl-event"         -> adString)
  )

  "An EventSerializer" should {
    val serializer = new EventSerializer(system.asInstanceOf[ExtendedActorSystem])

    "produce the correct event manifests" in {
      forAll(data.toList) {
        case (event, (manifest, _)) =>
          serializer.manifest(event) shouldEqual manifest
      }
    }

    "correctly serialize known events" in {
      forAll(data.toList) {
        case (event, (_, repr)) =>
          parse(new String(serializer.toBinary(event))).right.value shouldEqual parse(repr).right.value
      }
    }

    "correctly deserialize known events" in {
      forAll(data.toList) {
        case (event, (manifest, repr)) =>
          serializer.fromBinary(repr.getBytes, manifest) shouldEqual event
      }
    }

    "fail to produce a manifest" in {
      intercept[IllegalArgumentException](serializer.manifest("aaa"))
    }

    "fail to serialize an unknown type" in {
      intercept[IllegalArgumentException](serializer.toBinary("aaa"))
    }

    "fail to deserialize an unknown type" in {
      forAll(data.toList) {
        case (event, (manifest, repr)) =>
          intercept[IllegalArgumentException] {
            serializer.fromBinary((repr + "typo").getBytes, manifest) shouldEqual event
          }
      }
    }
  }

}
