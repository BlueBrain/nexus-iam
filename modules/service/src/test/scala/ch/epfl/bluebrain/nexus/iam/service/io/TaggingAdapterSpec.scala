package ch.epfl.bluebrain.nexus.iam.service.io

import java.time.Instant
import java.util.UUID

import akka.persistence.journal.Tagged
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.acls.Event.{
  PermissionsAdded,
  PermissionsCleared,
  PermissionsRemoved,
  PermissionsSubtracted
}
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.iam.service.io.TaggingAdapterSpec._
import ch.epfl.bluebrain.nexus.service.http.Path._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class TaggingAdapterSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks {

  private val adapter = new TaggingAdapter()
  val events =
    Table(
      "event",
      PermissionsCleared(path, meta),
      PermissionsRemoved(path, user, meta),
      PermissionsAdded(path, AccessControlList(user -> permissions, group -> permissions), meta),
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
  private val local       = "realm"
  private val user        = UserRef(local, "alice")
  private val group       = GroupRef(local, "some-group")
  private val meta        = Meta(user, Instant.ofEpochMilli(1))
  private val permissions = Permissions(Own, Read, Write)

  case class NonEvent(field: String)

}
