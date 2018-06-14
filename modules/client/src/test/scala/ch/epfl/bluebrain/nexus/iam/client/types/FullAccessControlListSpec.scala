package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.iam.client.types.Path._
import ch.epfl.bluebrain.nexus.iam.client.types.Permission._
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpecLike}

class FullAccessControlListSpec extends WordSpecLike with Matchers with Resources {

  "A FullAccessControlList" should {
    val publish     = Permission("publish")
    val permissions = Permissions(Own, Read, Write, publish)
    val group       = GroupRef("BBP", "some-group")
    val user        = UserRef("BBP", "user1")
    val path        = /
    val path1       = "a" / "path"
    val path2       = "another" / "path" / "longer"

    val model = FullAccessControlList(
      (group, path, Permissions(publish)),
      (group, path1, Permissions(Write, Read)),
      (group, path2, Permissions(Read)),
      (user, path1, Permissions(Own, Write, publish)),
      (user, path2, Permissions(Own))
    )

    val json = jsonContentOf("/types/identities-acls.json")

    "be encoded to Json properly" in {
      model.asJson shouldEqual json
    }

    "be decoded from Json properly" in {
      json.as[FullAccessControlList] shouldEqual Right(model)
    }

    "convert to aggregated path map" in {
      model.toPathMap shouldEqual Map(path  -> Permissions(publish),
                                      path1 -> permissions,
                                      path2 -> Permissions(Own, Read))
    }

    "convert to aggregated identity map" in {
      model.toIdentityMap shouldEqual Map(group -> Permissions(publish, Read, Write),
                                          user  -> Permissions(Own, Write, publish))
    }

    "collapse into permissions" in {
      model.permissions shouldEqual permissions
    }

    "check for any permission" in {
      model.hasAnyPermission(Permissions(Own, publish)) shouldEqual true
      model.hasAnyPermission(Permissions(publish, Permission("aa"))) shouldEqual true
      model.hasAnyPermission(Permissions.empty) shouldEqual false
      model.hasAnyPermission(Permissions(Permission("aa"), Permission("bb"))) shouldEqual false
    }

    "check for every permission" in {
      model.hasEveryPermission(permissions) shouldEqual true
      model.hasEveryPermission(Permissions(publish, Permission("aa"))) shouldEqual false
      model.hasEveryPermission(Permissions(Permission("aa"), Permission("bb"))) shouldEqual false
    }

  }

}
