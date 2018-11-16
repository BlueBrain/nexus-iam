package ch.epfl.bluebrain.nexus.iam.acls

import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.types.Identity._
import ch.epfl.bluebrain.nexus.iam.types.Permission
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

class AccessControlListsSpec extends WordSpecLike with Matchers with OptionValues {

  "AccessControlLists" should {
    val user: Identity  = User("uuid", "realm")
    val group: Identity = Group("group", "realm")

    val read: Permission  = Permission("read").value
    val write: Permission = Permission("write").value
    val other: Permission = Permission("other").value

    val acl  = AccessControlList(user  -> Set(read, write), group -> Set(other))
    val acl2 = AccessControlList(group -> Set(read))
    val acl3 = AccessControlList(group -> Set(other))

    "merge two ACLs" in {
      AccessControlLists(/           -> acl) ++ AccessControlLists(/ -> acl2, "a" / "b" -> acl3) shouldEqual
        AccessControlLists(/         -> AccessControlList(user -> Set(read, write), group -> Set(read, other)),
                           "a" / "b" -> acl3)

      AccessControlLists(/   -> acl) ++ AccessControlLists("a" / "b" -> acl2) shouldEqual
        AccessControlLists(/ -> acl, "a" / "b"                       -> acl2)
    }

    "add ACL" in {
      AccessControlLists(/           -> acl) + (/ -> acl2) + ("a" / "b" -> acl3) shouldEqual
        AccessControlLists(/         -> AccessControlList(user -> Set(read, write), group -> Set(read, other)),
                           "a" / "b" -> acl3)
    }
    "sort ACLs" in {
      AccessControlLists("aa" / "bb" -> acl, / -> acl3, "a" / "b" -> AccessControlList.empty, Path("a") -> acl2).sorted shouldEqual
        AccessControlLists(/ -> acl3, Path("a") -> acl2, "a" / "b" -> AccessControlList.empty, "aa" / "bb" -> acl)
    }

    "filter identities" in {
      AccessControlLists("a" / "b" -> acl, / -> acl2, "a" / "c" -> acl3).filter(Set(user)) shouldEqual
        AccessControlLists("a" / "b" -> AccessControlList(user -> Set(read, write)),
                           /         -> AccessControlList.empty,
                           "a" / "c" -> AccessControlList.empty)

      AccessControlLists("a" / "b" -> acl, / -> acl2, "a" / "c" -> acl3).filter(Set(group)) shouldEqual
        AccessControlLists("a" / "b" -> AccessControlList(group -> Set(other)), / -> acl2, "a" / "c" -> acl3)
    }

    "remove empty ACL" in {
      AccessControlLists(
        "a" / "b" -> acl,
        /         -> AccessControlList.empty,
        "a" / "c" -> AccessControlList(user -> Set(read, write), group -> Set.empty),
        "a" / "d" -> AccessControlList(user -> Set.empty, group -> Set.empty)
      ).removeEmpty shouldEqual
        AccessControlLists("a" / "b" -> acl, "a" / "c" -> AccessControlList(user -> Set(read, write)))
    }

    "remove ACL" in {
      acl -- acl2 shouldEqual acl
      acl -- AccessControlList(user -> Set(read), group                              -> Set(other)) shouldEqual AccessControlList(user -> Set(write))
      acl -- AccessControlList(user -> Set(read)) shouldEqual AccessControlList(user -> Set(write), group                              -> Set(other))
    }
  }

}
