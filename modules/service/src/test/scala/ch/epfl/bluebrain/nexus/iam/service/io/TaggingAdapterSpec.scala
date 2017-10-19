package ch.epfl.bluebrain.nexus.iam.service.io

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.core.acls.Event._
import ch.epfl.bluebrain.nexus.iam.core.acls.Path._
import ch.epfl.bluebrain.nexus.iam.core.acls.Permission.{Own, Read, Write}
import ch.epfl.bluebrain.nexus.iam.core.acls.{AccessControl, AccessControlList, Meta, Permissions}
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity.{GroupRef, UserRef}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}
import TaggingAdapterSpec._
import akka.persistence.journal.Tagged

class TaggingAdapterSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks {

  private val adapter = new TaggingAdapter()
  val events =
    Table(
      "event",
      PermissionsCleared(path, meta),
      PermissionsRemoved(path, user, meta),
      PermissionsCreated(path,
                         AccessControlList(
                           Set(
                             AccessControl(user, permissions),
                             AccessControl(group, permissions)
                           )),
                         meta),
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
  private val local       = Uri("http://localhost/realm")
  private val user        = UserRef(local, "alice")
  private val group       = GroupRef(local, "some-group")
  private val meta        = Meta(user, Instant.ofEpochMilli(1))
  private val permissions = Permissions(Own, Read, Write)

  case class NonEvent(field: String)

}
