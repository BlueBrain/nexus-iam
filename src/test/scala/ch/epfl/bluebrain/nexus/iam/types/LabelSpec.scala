package ch.epfl.bluebrain.nexus.iam.types

import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.rdf.Iri.{Path, Url}
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class LabelSpec extends WordSpecLike with Matchers with Randomness with Inspectors with EitherValues {

  "A Label" should {
    "be constructed correctly from alphanumeric chars" in {
      forAll(1 to 32) { length =>
        val string = genString(length, Vector.range('a', 'z') ++ Vector.range('0', '9'))
        Label.unsafe(string).value shouldEqual string
        Label(string).right.value.value shouldEqual string
      }
    }
    "fail to construct for illegal formats" in {
      val cases = List("", " ", "a ", " a", "a-", "_")
      forAll(cases) { string =>
        intercept[IllegalArgumentException](Label.unsafe(string))
        Label(string).left.value shouldEqual s"Label '$string' does not match pattern '${Label.regex.regex}'"
      }
    }
    "return its path representation" in {
      Label.unsafe("abc").toPath shouldEqual Path("/abc").right.value
    }
    "return an iri representation" in {
      forAll(List("http://localhost", "http://localhost/")) { str =>
        val base  = Url(str).right.value
        val label = Label.unsafe("abc")
        label.toIri(base) shouldEqual Url("http://localhost/abc").right.value
      }
    }
  }

}
