package ch.epfl.bluebrain.nexus.iam.elastic

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.elastic.SimpleIdentitySerialization._
import io.circe.syntax._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class SimpleIdentitySerializationSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks with Resources {
  val apiUri: Uri = Uri("http://localhost/prefix")

  private val realm     = "realm"
  private val user      = UserRef(realm, "alice")
  private val group     = GroupRef(realm, "some-group")
  private val auth      = AuthenticatedRef(Some("realm"))
  private val anonymous = Anonymous()

  val results = Table(
    ("identity", "json"),
    user      -> jsonContentOf("/identity/user.json"),
    group     -> jsonContentOf("/identity/group.json"),
    auth      -> jsonContentOf("/identity/auth.json"),
    anonymous -> jsonContentOf("/identity/anon.json")
  )

  "SimpleIdentitySerialization" should {
    "encoder events to JSON" in {
      forAll(results) { (event, json) =>
        event.asJson shouldBe json
      }
    }
    "decode events from JSON" in {
      forAll(results) { (event, json) =>
        json.as[Identity] shouldEqual Right(event)
      }
    }
  }
}
