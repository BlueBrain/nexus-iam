package ch.epfl.bluebrain.nexus.iam.core.groups

import java.util.UUID

import cats.instances.try_._
import ch.epfl.bluebrain.nexus.iam.types.Identity.GroupRef
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.{Success, Try}

class UsedGroupsSpec extends WordSpecLike with Matchers {
  val aggregate =
    MemoryAggregate("used-groups")(Set.empty[GroupRef], UsedGroups.next, UsedGroups.eval).toF[Try]

  val usedGroups = UsedGroups(aggregate)

  "UsedGroups" should {
    "add and retrieve groups for a realm" in {
      val realm1 = UUID.randomUUID().toString
      val realm1Groups = Set(GroupRef(realm1, UUID.randomUUID().toString),
                             GroupRef(realm1, UUID.randomUUID().toString),
                             GroupRef(realm1, UUID.randomUUID().toString))
      val realm2 = UUID.randomUUID().toString
      val realm2Groups = Set(
        GroupRef(realm2, UUID.randomUUID().toString),
        GroupRef(realm2, UUID.randomUUID().toString),
        GroupRef(realm2, UUID.randomUUID().toString),
        GroupRef(realm2, UUID.randomUUID().toString),
        GroupRef(realm2, UUID.randomUUID().toString)
      )

      realm1Groups.foreach(usedGroups.add)
      realm2Groups.foreach(usedGroups.add)

      usedGroups.fetch(realm1) shouldEqual Success(realm1Groups)
      usedGroups.fetch(realm2) shouldEqual Success(realm2Groups)

    }
    "only create event if the group doesn't exit yet" in {
      val realm  = UUID.randomUUID().toString
      val group1 = GroupRef(realm, UUID.randomUUID().toString)
      val group2 = GroupRef(realm, UUID.randomUUID().toString)

      1 to 100 foreach (_ => usedGroups.add(group1))
      1 to 100 foreach (_ => usedGroups.add(group2))
      1 to 100 foreach (_ => usedGroups.add(group1))

      aggregate.foldLeft(realm, 0) { (count, _) =>
        count + 1
      } shouldEqual Success(2)
    }
  }

}
