package ch.epfl.bluebrain.nexus.iam.realms
import java.time.Instant

import cats.effect.IO
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.iam.realms.RealmCommand._
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent._
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.RealmState._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

class RealmCommandSpec extends WordSpecLike with Matchers with OptionValues {

  val identity1 = Identity.Anonymous()
  val identity2 = UserRef("realm", "")
  val realm =
    Realm("realm1", url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value, Set("nexus"))
  "RealmCommand" should {

    "evaluate create realm command" in {
      val now = Instant.now()
      RealmCommand.evaluate[IO](Initial, CreateRealm(realm, 1, now, identity1)).unsafeRunSync() shouldEqual Right(
        RealmCreated(
          realm,
          1,
          now,
          identity1
        ))
    }
    "reject evaluate create realm command when state is not Initial" in {
      val now = Instant.now()

      RealmCommand
        .evaluate[IO](Current(realm, 1, false, now, now, identity1, identity2), CreateRealm(realm, 1, now, identity2))
        .unsafeRunSync() shouldEqual Left(RealmAlreadyExistsRejection)
    }

    "evaluate update command" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      val realmUpdate =
        Realm("realm1",
              url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value,
              Set("nexus", "another"))

      RealmCommand
        .evaluate[IO](Current(realm, 1, false, now, now, identity1, identity2),
                      UpdateRealm(realmUpdate, 1, updateTime, identity2))
        .unsafeRunSync() shouldEqual Right(RealmUpdated(realmUpdate, 2, updateTime, identity2))
    }
    "reject update command when state is Initial" in {
      val now = Instant.now()

      RealmCommand
        .evaluate[IO](Initial, UpdateRealm(realm, 1, now, identity1))
        .unsafeRunSync() shouldEqual Left(RealmDoesNotExistRejection)
    }
    "reject update command when the revisions don't match" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      val realmUpdate =
        Realm("realm1",
              url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value,
              Set("nexus", "another"))

      RealmCommand
        .evaluate[IO](Current(realm, 1, false, now, now, identity1, identity2),
                      UpdateRealm(realmUpdate, 2, updateTime, identity2))
        .unsafeRunSync() shouldEqual Left(IncorrectRevisionRejection(1, 2))
    }
    "reject update command when the realm is deprecated" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      val realmUpdate =
        Realm("realm1",
              url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value,
              Set("nexus", "another"))

      RealmCommand
        .evaluate[IO](Current(realm, 1, true, now, now, identity1, identity2),
                      UpdateRealm(realmUpdate, 1, updateTime, identity2))
        .unsafeRunSync() shouldEqual Left(RealmDeprecatedRejection)
    }

    "evaluate deprecate command" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      RealmCommand
        .evaluate[IO](Current(realm, 1, false, now, now, identity1, identity2),
                      DeprecateRealm(1, updateTime, identity2))
        .unsafeRunSync() shouldEqual Right(RealmDeprecated(2, updateTime, identity2))
    }
    "reject deprecate command when state is Initial" in {
      val now = Instant.now()
      RealmCommand
        .evaluate[IO](Initial, DeprecateRealm(1, now, identity1))
        .unsafeRunSync() shouldEqual Left(RealmDoesNotExistRejection)
    }
    "reject deprecate command when the revisions don't match" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      RealmCommand
        .evaluate[IO](Current(realm, 1, false, now, now, identity1, identity2),
                      DeprecateRealm(2, updateTime, identity2))
        .unsafeRunSync() shouldEqual Left(IncorrectRevisionRejection(1, 2))
    }
    "reject deprecate command when the realm is deprecated" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      RealmCommand
        .evaluate[IO](Current(realm, 1, true, now, now, identity1, identity2), DeprecateRealm(1, updateTime, identity2))
        .unsafeRunSync() shouldEqual Left(RealmDeprecatedRejection)
    }

  }

}
