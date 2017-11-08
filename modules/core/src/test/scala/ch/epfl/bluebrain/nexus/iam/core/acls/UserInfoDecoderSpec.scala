package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
import io.circe.parser.decode
import org.scalatest.{Matchers, WordSpecLike}

class UserInfoDecoderSpec extends WordSpecLike with Matchers {

  val userInfo =
    UserInfo("sub", "name", "preferredUsername", "givenName", "familyName", "email@example.com", Set("one", "two"))

  "A BBP UserInfo" should {
    import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.bbp._

    val userInfoString =
      s"""{"sub": "sub","name": "name","preferred_username": "preferredUsername","given_name": "givenName","family_name": "familyName","email": "email@example.com","groups": ["one","/two"]}""".stripMargin

    "be decoded properly" in {
      decode[UserInfo](userInfoString) shouldEqual Right(userInfo)
    }
  }

  "A HBP UserInfo" should {
    import ch.epfl.bluebrain.nexus.iam.core.acls.UserInfoDecoder.hbp._

    val userInfoString =
      s"""{"sub":"sub","name":"name","preferred_username":"preferredUsername","given_name":"givenName","family_name":"familyName","updated_at":"1509545255000","email": "email@example.com","groups":"one,/two"}""".stripMargin

    "be decoded properly" in {
      decode[UserInfo](userInfoString) shouldEqual Right(userInfo)
    }
  }

}
