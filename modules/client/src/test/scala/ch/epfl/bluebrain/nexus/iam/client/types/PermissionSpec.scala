package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.commons.test.Randomness
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class PermissionSpec extends WordSpecLike with Matchers with Inspectors with Randomness {

  "A Permission" should {
    val pool = Vector.range('a', 'z') ++ Vector.range('A', 'Z') ++ Vector('-', '_', '/')
    "matches the regex" in {
      val correct = Vector.fill(100)(genString(10, pool))
      forAll(correct)((string) => Permission(string).value shouldEqual string)
    }
    "should match the regex" in {
      val incorrect = List("some3", "OTHER*&^(")
      forAll(incorrect) { string =>
        intercept[IllegalArgumentException] {
          Permission(string)
        }
      }

    }
  }
}
