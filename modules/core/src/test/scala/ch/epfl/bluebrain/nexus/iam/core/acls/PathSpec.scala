package ch.epfl.bluebrain.nexus.iam.core.acls

import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}
import ch.epfl.bluebrain.nexus.iam.core.acls.Path._

class PathSpec extends WordSpecLike with Matchers with Inspectors {

  val path       = "a" / "b" / "c"
  val slash      = Path./
  val pathString = s""""${path.repr}""""

  "A Path" should {
    "be constructed correctly by parsing a string" in {
      val mapping = List("" -> slash,
                         "/"         -> slash,
                         "///"       -> slash,
                         "/ / /"     -> " " / " ",
                         "/a/b/c"    -> path,
                         "/a//b/c"   -> path,
                         "/a/\\/b/c" -> "a" / "\\" / "b" / "c")
      forAll(mapping) {
        case (tested, expected) =>
          Path(tested) should equal(expected)
      }
    }
    "be constructed correctly from a lifted string" in {
      path.repr should equal("/a/b/c")
    }
    "be reversed correctly" when {
      "empty" in {
        slash.reverse should equal(slash)
      }
      "not empty" in {
        path.reverse.repr should equal("/c/b/a")
      }
    }
    "present its appropriate head" when {
      "empty" in {
        slash.head should equal(slash)
      }
      "not empty" in {
        path.head should equal("c")
      }
    }
    "present its appropriate tail" when {
      "empty" in {
        slash.tail should equal(slash)
      }
      "not empty" in {
        path.tail should equal("a" / "b")
      }
    }
    "present its length" when {
      "empty" in {
        slash.length should equal(0)
      }
      "not empty" in {
        path.length should equal(3)
      }
    }
    "present its empty state" when {
      "empty" in {
        slash.isEmpty should equal(true)
      }
      "not empty" in {
        path.isEmpty should equal(false)
      }
    }
    "join correctly with an empty path" when {
      "empty" in {
        slash ++ slash should equal(slash)
      }
      "not empty" in {
        path ++ slash should equal(path)
      }
    }
    "join correctly with a non empty path" when {
      "empty" in {
        slash ++ path should equal(path)
      }
      "not empty" in {
        path ++ path should equal(path / "a" / "b" / "c")
      }
    }
    "allow single segment construction" in {
      Path./("a").repr should equal("/a")
    }
    "be encoded properly" in {
      path.asJson.noSpaces shouldEqual pathString
    }
    "be decoded properly" in {
      decode[Path](pathString) shouldEqual Right(path)
    }
  }
}
