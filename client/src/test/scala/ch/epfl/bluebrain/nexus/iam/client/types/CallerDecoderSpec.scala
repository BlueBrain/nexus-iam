package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.commons.test.{EitherValues, Resources}
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{Anonymous, Authenticated, Group, User}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class CallerDecoderSpec extends AnyWordSpecLike with Matchers with Resources with EitherValues {

  "Caller decoder" should {

    "decode subject correctly" in {

      jsonContentOf("/identities/caller.json").as[Caller].rightValue shouldEqual Caller(
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
