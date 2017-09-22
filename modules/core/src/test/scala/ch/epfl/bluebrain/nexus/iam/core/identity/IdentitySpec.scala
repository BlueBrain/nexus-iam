package ch.epfl.bluebrain.nexus.iam.core.identity

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._
import io.circe.DecodingFailure
import io.circe.parser.decode
import io.circe.syntax._
import org.scalatest._

import scala.util._

class IdentitySpec extends WordSpecLike with Matchers with Inspectors {

  private val values = List(
    ("{}", Anonymous),
    ("""{"origin":"http://localhost/realm"}""", AuthenticatedRef(Uri("http://localhost/realm"))),
    ("""{"origin":"http://localhost/realm","group":"some-group"}""",
     GroupRef(Uri("http://localhost/realm"), "some-group")),
    ("""{"origin":"http://localhost/realm","subject":"alice"}""", UserRef(Uri("http://localhost/realm"), "alice"))
  )

  "An Identity" should {
    "be decoded from Json properly" in {
      forAll(values) {
        case (json, id) => decode[Identity](json) shouldEqual Right(id)
      }

      decode[Identity]("""{"origin":"http://localhost/realm","group":"will-be-ignored","subject":"bob"}""") shouldEqual
        Right(UserRef(Uri("http://localhost/realm"), "bob"))
    }
    "be encoded to Json properly" in {
      forAll(values) {
        case (json, id) => id.asJson.noSpaces shouldEqual json
      }
    }
    "not be decoded when origin URI is bogus" in {
      decode[Identity]("""{"origin":"föó://bar","subject":"bob"}""") match {
        case Right(_) => fail()
        case Left(e) => e shouldBe a[DecodingFailure]
      }
    }
  }
}
