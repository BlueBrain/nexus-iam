package ch.epfl.bluebrain.nexus.iam.realms
import java.time.Instant

import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent._
import ch.epfl.bluebrain.nexus.iam.realms.RealmState._
import org.scalatest.{Matchers, OptionValues, WordSpecLike}

class RealmStateSpec extends WordSpecLike with Matchers with OptionValues {

  val identity1 = Identity.Anonymous()
  val identity2 = UserRef("realm", "")
  val realm =
    Realm("realm1", url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value, Set("nexus"))

  "RealmState" should {

    "calculate new state from initial" in {
      val now = Instant.now()
      val realm =
        Realm("realm1", url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value, Set("nexus"))

      RealmState.next(Initial, RealmCreated(realm, 1, now, identity1)) shouldEqual Current(realm,
                                                                                           1,
                                                                                           false,
                                                                                           now,
                                                                                           now,
                                                                                           identity1,
                                                                                           identity1)

    }

    "calculate new state from update" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      val realmUpdate =
        Realm("realm1",
              url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value,
              Set("nexus", "another"))

      RealmState.next(Current(realm, 1, false, now, now, identity1, identity1),
                      RealmUpdated(realmUpdate, 2, updateTime, identity2)) shouldEqual Current(realmUpdate,
                                                                                               2,
                                                                                               false,
                                                                                               now,
                                                                                               updateTime,
                                                                                               identity1,
                                                                                               identity2)
    }
    "calculate new state from deprecation" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)

      RealmState.next(Current(realm, 1, false, now, now, identity1, identity1),
                      RealmDeprecated(2, updateTime, identity2)) shouldEqual Current(realm,
                                                                                     2,
                                                                                     true,
                                                                                     now,
                                                                                     updateTime,
                                                                                     identity1,
                                                                                     identity2)

    }
    "ignore other updates" in {
      val now        = Instant.now()
      val updateTime = now.plusSeconds(10)
      val realmUpdate =
        Realm("realm1",
              url"http://nexus.example.com/realm1/openid-configuration".value.asUrl.value,
              Set("nexus", "another"))

      val current = Current(realm, 1, false, now, now, identity1, identity1)

      RealmState.next(current, RealmCreated(realmUpdate, 2, updateTime, identity2)) shouldEqual current

    }

  }
}
