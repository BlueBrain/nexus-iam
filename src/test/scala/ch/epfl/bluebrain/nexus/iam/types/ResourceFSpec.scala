package ch.epfl.bluebrain.nexus.iam.types

import java.time.{Clock, Instant, ZoneId}

import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.Identity.User
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import io.circe.syntax._
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class ResourceFSpec extends WordSpecLike with Matchers with Inspectors with EitherValues with Resources {

  "A ResourceMetadata" should {
    val user          = User("mysubject", "myrealm")
    val user2         = User("mysubject2", "myrealm")
    implicit val http = HttpConfig("some", 8080, "v1", "http://nexus.example.com")
    val clock: Clock  = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
    val instant       = clock.instant()
    val id            = url"http://example.com/id"

    "converted to Json" in {
      val json  = jsonContentOf("/resources/write-response.json")
      val model = ResourceMetadata(id, 1L, Set(nxv.AccessControlList, nxv.Realm), instant, user, instant, user2)

      model.asJson shouldEqual json
    }
  }
}
