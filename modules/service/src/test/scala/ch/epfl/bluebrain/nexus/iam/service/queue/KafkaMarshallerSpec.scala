package ch.epfl.bluebrain.nexus.iam.service.queue

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permissions
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.acls.Event._
import ch.epfl.bluebrain.nexus.iam.core.acls.{Event, Meta}
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.Encoder
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class KafkaMarshallerSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks {
  implicit val apiUri: ApiUri        = ApiUri(Uri("http://localhost/prefix"))
  implicit val evEnc: Encoder[Event] = KafkaEncoders.eventEncoder
  private val m                      = KafkaMarshaller.jsonLdMarshaller[Event]
  private val uuid                   = UUID.randomUUID.toString
  private val path                   = "foo" / "bar" / uuid
  private val local                  = "realm"
  private val user                   = UserRef(local, "alice")
  private val group                  = GroupRef(local, "some-group")
  private val meta                   = Meta(user, Instant.ofEpochMilli(1))
  private val permissions            = Permissions(Own, Read, Write)

  private val context    = s""""${apiUri.base.withPath(apiUri.base.path / "context")}""""
  private val pathString = s""""${path.repr}""""
  private val groupString =
    s"""{"@id":"http://localhost/prefix/realms/realm/groups/some-group","realm":"realm","group":"some-group","@type":"GroupRef"}"""
  private val userString =
    s"""{"@id":"http://localhost/prefix/realms/realm/users/alice","realm":"realm","sub":"alice","@type":"UserRef"}"""
  private val permissionsString = s"""["own","read","write"]"""

  val results = Table(
    ("event", "json"),
    (PermissionsAdded(path, group, permissions, meta),
     s"""{"@context":$context,"path":$pathString,"identity":$groupString,"permissions":$permissionsString,"@type":"PermissionsAdded"}"""),
    (
      PermissionsSubtracted(path, user, permissions, meta),
      s"""{"@context":$context,"path":$pathString,"identity":$userString,"permissions":$permissionsString,"@type":"PermissionsSubtracted"}"""
    ),
    (PermissionsRemoved(path, group, meta),
     s"""{"@context":$context,"path":$pathString,"identity":$groupString,"@type":"PermissionsRemoved"}"""),
    (PermissionsCleared(path, meta), s"""{"@context":$context,"path":$pathString,"@type":"PermissionsCleared"}""")
  )

  "KafkaEncoders" should {
    "encoder events to JSON" in {
      forAll(results) { (event, json) =>
        m(event) shouldBe json

      }
    }
  }

}
