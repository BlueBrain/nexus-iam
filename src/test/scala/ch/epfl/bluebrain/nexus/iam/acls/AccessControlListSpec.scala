package ch.epfl.bluebrain.nexus.iam.acls

import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.types.Identity._
import ch.epfl.bluebrain.nexus.iam.types.{Identity, Permission}
import io.circe.syntax._
import org.scalatest._

class AccessControlListSpec
    extends WordSpecLike
    with Matchers
    with Inspectors
    with EitherValues
    with OptionValues
    with Resources {

  "An Access Control List" should {
    val user: Identity  = User("mysubject", "myrealm")
    val group: Identity = Group("mygroup", "myrealm")
    val readWrite       = Set(Permission("acls/read").value, Permission("acls/write").value)
    val manage          = Set(Permission("acls/manage").value)
    implicit val http   = HttpConfig("some", 8080, "v1", "http://nexus.example.com")

    "converted to Json" in {
      val acls = AccessControlList(user -> readWrite, group -> manage)
      val json = jsonContentOf("/acls/acl.json")
      acls.asJson shouldEqual json
    }
    "convert from Json" in {
      val acls = AccessControlList(user -> readWrite, group -> manage)
      val json = jsonContentOf("/acls/acl.json")
      json.as[AccessControlList].right.value shouldEqual acls
    }
  }
}
