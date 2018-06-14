package ch.epfl.bluebrain.nexus.iam.client.types

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.client.types.Path._
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Inspectors, Matchers, WordSpecLike}

class PathSpec extends WordSpecLike with Matchers with Inspectors {

  val path: Path = "a" / "b" / "c"
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
    "convert from Uri.Path to Path correctly" in {
      (Uri.Path("/a/b/c"): Path) shouldEqual path
    }
    "convert from Path to Uri.Path correctly" in {
      (path: Uri.Path) shouldEqual Uri.Path("/a/b/c")
    }

    "check if startsWith another Path" in {
      path startsWith / shouldEqual true
      path startsWith Path("a") shouldEqual true
      path startsWith "a" / "b" shouldEqual true
      path startsWith "a" / "b" / "c" shouldEqual true
      path startsWith "c" / "b" / "a" shouldEqual false
      path startsWith "a" / "b" / "c" / "d" shouldEqual false
      / startsWith / shouldEqual true
      / startsWith path shouldEqual false
    }

    "get segments" in {
      path.segments shouldEqual List("a", "b", "c")
      /.segments shouldEqual List.empty[String]

    }

    "Append a path without adding double slash" in {
      val list = List(
        (Uri("http://localhost/a"), Path("/b"), Uri("http://localhost/a/b")),
        (Uri("http://localhost/a/"), Path("b"), Uri("http://localhost/a/b")),
        (Uri("http://localhost/"), "b" / "c", Uri("http://localhost/b/c")),
        (Uri("http://localhost"), "b" / "c", Uri("http://localhost/b/c"))
      )

      forAll(list) {
        case (base, path, result) => base.append(path) shouldEqual result
      }
    }

  }
}
