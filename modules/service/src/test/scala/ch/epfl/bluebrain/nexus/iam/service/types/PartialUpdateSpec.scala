package ch.epfl.bluebrain.nexus.iam.service.types

import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.SimpleIdentitySerialization._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import io.circe.Json
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class PartialUpdateSpec extends WordSpecLike with Matchers with Resources with TableDrivenPropertyChecks {

  "A PartialUpdate" should {
    val results = Table[Json, PartialUpdate](
      "json" -> "event",
      jsonContentOf("/patch/subtract_1.json") -> Subtract(UserRef("realm",
                                                                  "f:9d46ddd6-134e-44d6-aa74-bdf00f48dfce:dmontero"),
                                                          Permissions(Read, Permission("other"))),
      jsonContentOf("/patch/subtract_2.json") -> Subtract(GroupRef("BBP", "group"), Permissions(Read, Write))
    )

    "decode patch data types from JSON" in {
      forAll(results) { (json, event) =>
        json.as[PartialUpdate] shouldEqual Right(event)
      }
    }
  }
}
