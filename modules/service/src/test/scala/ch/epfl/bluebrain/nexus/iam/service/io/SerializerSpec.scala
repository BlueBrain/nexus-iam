package ch.epfl.bluebrain.nexus.iam.service.io

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.serialization.{SerializationExtension, SerializerWithStringManifest}
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.iam.types.Identity.{GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import ch.epfl.bluebrain.nexus.iam.core.acls.Event._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.iam.service.io.Serializer.EventSerializer
import ch.epfl.bluebrain.nexus.iam.service.io.SerializerSpec.{DataAndJson, results}
import ch.epfl.bluebrain.nexus.service.http.Path._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import shapeless.Typeable

class SerializerSpec extends WordSpecLike with Matchers with Inspectors with ScalatestRouteTest {

  private val serialization = SerializationExtension(system)

  def findConcreteSerializer[A <: SerializerWithStringManifest](o: AnyRef)(implicit t: Typeable[A]): A = {
    t.cast(serialization.findSerializerFor(o)).getOrElse(fail("Expected a SerializerWithManifest"))
  }

  "A Serializer" when {
    "using EventSerializer" should {
      "encode known events to UTF-8" in {
        forAll(results) {
          case DataAndJson(event, json, _) =>
            val serializer = findConcreteSerializer[EventSerializer](event)
            new String(serializer.toBinary(event), StandardCharsets.UTF_8) shouldEqual json
        }
      }

      "decode known events" in {
        forAll(results) {
          case DataAndJson(event, json, manifest) =>
            val serializer = findConcreteSerializer[EventSerializer](event)
            serializer.fromBinary(json.getBytes(StandardCharsets.UTF_8), manifest) shouldEqual event
        }
      }
    }
  }
}

object SerializerSpec {

  /**
    * Holds both the JSON representation and the data structure
    *
    * @param data     instance of the data as a data structure
    * @param json     the JSON representation of the data
    * @param manifest the manifest to be used for selecting the appropriate resulting type
    */
  case class DataAndJson[A](data: A, json: String, manifest: String)

  object DataAndJson {
    def apply[A](data: A, json: String)(implicit tb: Typeable[A]): DataAndJson[A] =
      DataAndJson[A](data, json, tb.describe)
  }

  private val uuid        = UUID.randomUUID.toString
  private val path        = "foo" / "bar" / uuid
  private val realm       = "realm"
  private val author      = UserRef(realm, "alice")
  private val group       = GroupRef(realm, "some-group")
  private val meta        = Meta(author, Instant.ofEpochMilli(1))
  private val permissions = Permissions(Own, Read, Write)

  private val pathString        = s""""${path.repr}""""
  private val identityString    = s"""{"id":"realms/realm/groups/some-group","type":"GroupRef"}"""
  private val authorString      = s"""{"id":"realms/realm/users/alice","type":"UserRef"}"""
  private val metaString        = s"""{"author":$authorString,"instant":"1970-01-01T00:00:00.001Z"}"""
  private val permissionsString = s"""["own","read","write"]"""

  val results = List(
    DataAndJson[Event](
      PermissionsAdded(path, AccessControlList(group -> permissions), meta),
      s"""{"path":$pathString,"acl":{"acl":[{"identity":$identityString,"permissions":$permissionsString}]},"meta":$metaString,"type":"PermissionsAdded"}"""
    ),
    DataAndJson[Event](
      PermissionsSubtracted(path, group, permissions, meta),
      s"""{"path":$pathString,"identity":$identityString,"permissions":$permissionsString,"meta":$metaString,"type":"PermissionsSubtracted"}"""
    ),
    DataAndJson[Event](
      PermissionsRemoved(path, group, meta),
      s"""{"path":$pathString,"identity":$identityString,"meta":$metaString,"type":"PermissionsRemoved"}"""),
    DataAndJson[Event](PermissionsCleared(path, meta),
                       s"""{"path":$pathString,"meta":$metaString,"type":"PermissionsCleared"}""")
  )
}
