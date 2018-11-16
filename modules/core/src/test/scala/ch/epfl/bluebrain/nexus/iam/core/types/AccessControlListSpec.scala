package ch.epfl.bluebrain.nexus.iam.core.types

import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.GroupRef
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControl, AccessControlList, Permission, Permissions}
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import io.circe.Printer
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

class AccessControlListSpec extends WordSpecLike with Matchers {

  "A AccessControlList" should {
    val Publish                        = Permission("publish")
    val permissions                    = Permissions(Own, Read, Write, Publish)
    implicit val config: Configuration = Configuration.default.withDiscriminator("type")
    val printer                        = Printer.noSpaces.copy(dropNullValues = true)
    val model                          = AccessControlList(Set(AccessControl(GroupRef("BBP", "some-group"), permissions)))
    val json =
      """{"acl":[{"identity":{"id":"realms/BBP/groups/some-group","type":"GroupRef"},"permissions":["own","read","write","publish"]}]}"""

    "be decoded from Json properly" in {
      decode[AccessControlList](json) shouldEqual Right(model)
    }

    "be encoded to Json properly" in {
      printer.pretty(model.asJson) shouldEqual json
    }

    "convert to map" in {
      val identity = GroupRef("BBP", "some-group")
      model.toMap shouldEqual Map(identity -> Permissions(Own, Read, Write, Publish))
    }
    "check if it has void permissions" in {
      model.hasVoidPermissions shouldEqual false
      AccessControlList().hasVoidPermissions shouldEqual true
    }

    "collapse into Permissions" in {
      val permission = Permission("something")
      val acls = AccessControlList(
        Set(AccessControl(GroupRef("BBP", "some-group"), permissions),
            AccessControl(GroupRef("BBP", "something"), Permissions(permission, Own))))
      acls.permissions shouldEqual Permissions(Own, Read, Write, Publish, permission)
    }

  }

}
