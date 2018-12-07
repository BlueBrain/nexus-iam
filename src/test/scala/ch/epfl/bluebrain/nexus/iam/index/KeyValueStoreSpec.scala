package ch.epfl.bluebrain.nexus.iam.index

import cats.effect.IO
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.index.KeyValueStoreSpec._
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import ch.epfl.bluebrain.nexus.sourcing.akka.RetryStrategy
import org.scalatest.Matchers

import scala.concurrent.duration._

class KeyValueStoreSpec
    extends ActorSystemFixture("KeyValueStoreSpec", true)
    with Matchers
    with IOEitherValues
    with IOOptionValues {



  "A KeyValueStore" should {

    val store = KeyValueStore.distributed[IO, String, RevisionedValue[String]](
      "spec",
      { case (_, rv) => rv.rev },
      3 seconds,
      3 seconds,
      RetryStrategy.never
    )

    "store values" in {
      store.put("a", RevisionedValue(1, "a")).ioValue
      store.get("a").some shouldEqual RevisionedValue(1, "a")
    }

    "update values" in {
      store.put("a", RevisionedValue(2, "aa")).ioValue
      store.get("a").some shouldEqual RevisionedValue(2, "aa")
    }

    "discard updates for previous revisions" in {
      store.put("a", RevisionedValue(1, "a")).ioValue
      store.get("a").some shouldEqual RevisionedValue(2, "aa")
    }

    "return all entries" in {
      store.put("b", RevisionedValue(1, "b")).ioValue
      store.entries().ioValue shouldEqual Map(
        "b" -> RevisionedValue(1, "b"),
        "a" -> RevisionedValue(2, "aa")
      )
    }

    "return all values" in {
      store.values().ioValue shouldEqual Set(RevisionedValue(1, "b"), RevisionedValue(2, "aa"))
    }

    "return a matching (key, value)" in {
      store.find({ case (k, _) => k == "a" }).some shouldEqual ("a" -> RevisionedValue(2, "aa"))
    }

    "fail to return a matching (key, value)" in {
      store.find({ case (k, _) => k == "c" }).ioValue.isEmpty shouldEqual true
    }

    "return a matching value" in {
      store.findValue(_.value == "aa").some shouldEqual RevisionedValue(2, "aa")
    }

    "fail to return a matching value" in {
      store.findValue(_.value == "cc").ioValue.isEmpty shouldEqual true
    }

    "return empty entries" in {
      KeyValueStore.distributed[IO, String, RevisionedValue[String]](
        "empty",
        { case (_, rv) => rv.rev },
        3 seconds,
        3 seconds,
        RetryStrategy.never
      ).entries().ioValue shouldEqual Map.empty[String, RevisionedValue[String]]
    }

  }

}

object KeyValueStoreSpec {
  final case class RevisionedValue[A](rev: Long, value: A)
}