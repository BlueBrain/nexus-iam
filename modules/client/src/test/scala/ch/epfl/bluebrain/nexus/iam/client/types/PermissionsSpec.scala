package ch.epfl.bluebrain.nexus.iam.client.types

import org.scalatest.{Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.iam.client.types.Permission._

class PermissionsSpec extends WordSpecLike with Matchers {
  "A Permissions" should {
    val permissions = Permissions(Write)

    "add a permission to the permissions" in {
      permissions + Read shouldEqual Permissions(Write, Read)
    }

    "subtract a permission to the permissions" in {
      (permissions + Read - Write) shouldEqual Permissions(Read)
    }

    "add permissions to the permissions" in {
      permissions ++ Permissions(Read, Own) shouldEqual Permissions(Write, Read, Own)
    }

    "subtract permissions to the permissions" in {
      (permissions + Read) -- Permissions(Write, Own) shouldEqual Permissions(Read)
    }

    "show permissions with match two permissions" in {
      Permissions(Read, Write, Own) intersect (Permissions(Write)) shouldEqual (Permissions(Write))
      Permissions(Read, Write) & (Permissions(Own)) shouldEqual (Permissions())
    }

    "check if a permission is contained on permissions" in {
      Permissions(Read, Write).contains(Read) shouldEqual true
      Permissions(Read, Write).contains(Own) shouldEqual false
    }

    "check if all provided permissions are contained on permissions" in {
      Permissions(Read, Write).containsAll(Permissions(Read)) shouldEqual true
      Permissions(Read, Write).containsAll(Permissions(Read, Own)) shouldEqual false
    }

    "check if some provided permissions are contained on permissions" in {
      Permissions(Read, Write).containsAny(Permissions(Own, Read)) shouldEqual true
      Permissions(Read, Write).containsAny(Permissions(Own, Permission("something"))) shouldEqual false
    }
  }
}
