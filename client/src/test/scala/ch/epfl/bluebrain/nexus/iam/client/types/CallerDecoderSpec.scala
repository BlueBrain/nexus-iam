package ch.epfl.bluebrain.nexus.iam.client.types
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{Anonymous, Authenticated, Group, User}
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class CallerDecoderSpec extends WordSpecLike with Matchers with Resources with EitherValues {

  "Caller decoder" should {

    "decode subject correctly" in {

      jsonContentOf("/identities/caller.json").as[Caller].right.value shouldEqual Caller(
        User("nexus-test-user", "nexusdev"),
        Set(
          Anonymous,
          Authenticated("nexusdev"),
          Group("nexus-test-group", "nexusdev"),
          User("nexus-test-user", "nexusdev")
        )
      )

    }
  }

}
