package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class IdentitySpec extends WordSpecLike with Matchers with Inspectors with Resources {

  private val realm = "realm"

  val results: List[(Identity, Json)] = List(
    UserRef(realm, "alice")         -> jsonContentOf("/identity/user.json"),
    GroupRef(realm, "some-group")   -> jsonContentOf("/identity/group.json"),
    AuthenticatedRef(Some("realm")) -> jsonContentOf("/identity/auth.json"),
    Anonymous                       -> jsonContentOf("/identity/anon.json")
  )

  "An Identity" should {
    "encoder events to JSON" in {
      forAll(results) {
        case (event, json) =>
          event.asJson shouldBe json
      }
    }
    "decode events from JSON" in {
      forAll(results) {
        case (event, json) =>
          json.as[Identity] shouldEqual Right(event)
      }
    }
  }
}
