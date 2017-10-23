package ch.epfl.bluebrain.nexus.iam.service.queue

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.core.acls.Event.{
  PermissionsAdded,
  PermissionsCleared,
  PermissionsRemoved,
  PermissionsSubtracted
}
import ch.epfl.bluebrain.nexus.iam.core.acls.Path._
import ch.epfl.bluebrain.nexus.iam.core.acls.Permission.{Own, Read, Write}
import ch.epfl.bluebrain.nexus.iam.core.acls.{Meta, Permissions}
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity.{GroupRef, Realm, UserRef}
import io.circe.syntax._
import io.circe.generic.extras.auto._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class KafkaEncoderSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks with KafkaEncoder {
  override val baseUri    = Uri("http://localhost/prefix")
  private val uuid        = UUID.randomUUID.toString
  private val path        = "foo" / "bar" / uuid
  private val local       = Realm("local", Uri("http://localhost/realm"))
  private val user        = UserRef(local, "alice")
  private val group       = GroupRef(local, "some-group")
  private val meta        = Meta(user, Instant.ofEpochMilli(1))
  private val permissions = Permissions(Own, Read, Write)

  private val context    = s""""${baseUri.withPath(baseUri.path / "context")}""""
  private val pathString = s""""${path.repr}""""
  private val groupString =
    s"""{"@context":$context,"@id":"http://localhost/prefix/realms/local/groups/some-group","realm":{"name":"local","uri":"http://localhost/realm"},"group":"some-group","@type":"GroupRef"}"""
  private val userString =
    s"""{"@context":$context,"@id":"http://localhost/prefix/realms/local/users/alice","realm":{"name":"local","uri":"http://localhost/realm"},"subject":"alice","@type":"UserRef"}"""
  private val permissionsString = s"""["own","read","write"]"""

  val results = Table(
    ("event", "json"),
    (PermissionsAdded(path, group, permissions, meta),
     s"""{"path":$pathString,"identity":$groupString,"permissions":$permissionsString,"type":"PermissionsAdded"}"""),
    (
      PermissionsSubtracted(path, user, permissions, meta),
      s"""{"path":$pathString,"identity":$userString,"permissions":$permissionsString,"type":"PermissionsSubtracted"}"""
    ),
    (PermissionsRemoved(path, group, meta),
     s"""{"path":$pathString,"identity":$groupString,"type":"PermissionsRemoved"}"""),
    (PermissionsCleared(path, meta), s"""{"path":$pathString,"type":"PermissionsCleared"}""")
  )

  "KafkaEncoder" should {
    "encoder events to JSON" in {
      forAll(results) { (event, json) =>
        println(event.asJson.pretty(printer))
        println(json)
        event.asJson.pretty(printer) shouldBe json

      }
    }
  }

}
