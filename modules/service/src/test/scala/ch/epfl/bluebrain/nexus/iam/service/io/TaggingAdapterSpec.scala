package ch.epfl.bluebrain.nexus.iam.service.io

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri
import akka.persistence.journal.Tagged
import ch.epfl.bluebrain.nexus.iam.core.acls.Event._
import ch.epfl.bluebrain.nexus.iam.core.acls.Path._
import ch.epfl.bluebrain.nexus.iam.core.acls.Permission.{Own, Read, Write}
import ch.epfl.bluebrain.nexus.iam.core.acls.{AccessControlList, Meta, Permissions}
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity.{GroupRef, Realm, UserRef}
import ch.epfl.bluebrain.nexus.iam.service.io.TaggingAdapterSpec._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class TaggingAdapterSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks {

  private val adapter = new TaggingAdapter()
  val events =
    Table(
      "event",
      PermissionsCleared(path, meta),
      PermissionsRemoved(path, user, meta),
      PermissionsCreated(path, AccessControlList(user -> permissions, group -> permissions), meta),
      PermissionsAdded(path, user, permissions, meta),
      PermissionsSubtracted(path, user, permissions, meta)
    )

  "TaggingAdapter" should {

    "tag permissions events" in {
      forAll(events) { event =>
        adapter.toJournal(event) shouldBe Tagged(event, Set("permission"))
      }
    }

    "pass through other objects" in {
      val nonEvent = NonEvent("test")
      adapter.toJournal(nonEvent) shouldBe nonEvent
    }
  }
}

object TaggingAdapterSpec {

  private val uuid        = UUID.randomUUID.toString
  private val path        = "foo" / "bar" / uuid
  private val local       = Realm("local", Uri("http://localhost/realm"))
  private val user        = UserRef(local, "alice")
  private val group       = GroupRef(local, "some-group")
  private val meta        = Meta(user, Instant.ofEpochMilli(1))
  private val permissions = Permissions(Own, Read, Write)

  case class NonEvent(field: String)

}
