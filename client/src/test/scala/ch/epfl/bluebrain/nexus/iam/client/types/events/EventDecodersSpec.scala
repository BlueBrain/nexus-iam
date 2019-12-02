package ch.epfl.bluebrain.nexus.iam.client.types.events

import java.time.Instant

import ch.epfl.bluebrain.nexus.commons.test.{EitherValues, Resources}
import ch.epfl.bluebrain.nexus.iam.client.types.GrantType._
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.{Anonymous, User}
import ch.epfl.bluebrain.nexus.iam.client.types.events.Event._
import ch.epfl.bluebrain.nexus.iam.client.types.{AccessControlList, Permission}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import io.circe.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

//noinspection TypeAnnotation
class EventDecodersSpec extends AnyWordSpecLike with Matchers with Resources with EitherValues {

  "Encoders and decoders" when {
    val read: Permission   = Permission.unsafe("read")
    val write: Permission  = Permission.unsafe("write")
    val create: Permission = Permission.unsafe("create")
    val instant            = Instant.EPOCH
    val subject            = User("myuser", "myrealm")

    "dealing with ACL events" should {

      val acl = AccessControlList(Anonymous -> Set(read), User("some", "myrealm") -> Set(read, write))

      "decode acl replaced event" in {
        val expected: AclEvent = AclReplaced("one" / "two", acl, 1L, instant, subject)
        val json               = jsonContentOf("/events/acl-replaced.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode acl appended event" in {
        val appendAcls         = AccessControlList(User("some", "myrealm") -> Set(create, read))
        val expected: AclEvent = AclAppended("one" / "two", appendAcls, 2L, instant, subject)
        val json               = jsonContentOf("/events/acl-appended.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode acl subtracted event" in {
        val expected: AclEvent = AclSubtracted(/, AccessControlList(Anonymous -> Set(read)), 2L, instant, subject)
        val json               = jsonContentOf("/events/acl-subtracted.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode acl deleted event" in {
        val expected: AclEvent = AclDeleted("one" / "two", 3L, instant, subject)
        val json               = jsonContentOf("/events/acl-deleted.json")
        json.as[Event].rightValue shouldEqual expected
      }
    }

    "dealing with Permission events" should {

      "decode permissions replaced event" in {
        val expected: PermissionsEvent = PermissionsReplaced(Set(read, write), 1L, instant, subject)
        val json                       = jsonContentOf("/events/permissions-replaced.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode permissions appended event" in {
        val expected: PermissionsEvent = PermissionsAppended(Set(read), 2L, instant, subject)
        val json                       = jsonContentOf("/events/permissions-appended.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode permissions subtracted event" in {
        val expected: PermissionsEvent = PermissionsSubtracted(Set(read, create), 3L, instant, subject)
        val json                       = jsonContentOf("/events/permissions-subtracted.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode permissions deleted event" in {
        val expected: PermissionsEvent = PermissionsDeleted(4L, instant, subject)
        val json                       = jsonContentOf("/events/permissions-deleted.json")
        json.as[Event].rightValue shouldEqual expected
      }
    }

    "dealing with Realm events" should {

      val key: Json = Json.obj(
        "alg" -> Json.fromString("RS256"),
        "e"   -> Json.fromString("AQAB"),
        "kid" -> Json.fromString("kidvalue"),
        "kty" -> Json.fromString("RSA"),
        "n"   -> Json.fromString("nvalue"),
        "use" -> Json.fromString("sig")
      )
      val openIdConfig = Iri.url("http://nexus.example.com/auth/realms/myrealm/openid-configuration").rightValue
      val authorization =
        Iri.url("http://nexus.example.com/auth/realms/myrealm/protocol/openid-connect/auth").rightValue
      val token = Iri.url("http://nexus.example.com/auth/realms/myrealm/protocol/openid-connect/token").rightValue
      val userInfo =
        Iri.url("http://nexus.example.com/auth/realms/myrealm/protocol/openid-connect/userinfo").rightValue
      val endSession =
        Iri.url("http://nexus.example.com/auth/realms/myrealm/protocol/openid-connect/logout").rightValue
      val issuer = "http://nexus.example.com/auth/realms/myrealm"

      "decode realm created event" in {
        val expected: RealmEvent = RealmCreated(
          "nexusdev",
          1L,
          "Nexus Dev",
          openIdConfig,
          issuer,
          Set(key),
          Set(Password, ClientCredentials, RefreshToken, AuthorizationCode, Implicit),
          None,
          authorization,
          token,
          userInfo,
          None,
          Some(endSession),
          instant,
          subject
        )
        val json = jsonContentOf("/events/realm-created.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode realm updated event" in {
        val expected: RealmEvent = RealmUpdated(
          "nexusdev",
          2L,
          "Nexus Dev Updated",
          openIdConfig,
          issuer,
          Set(key),
          Set(Password, ClientCredentials, RefreshToken, AuthorizationCode, Implicit),
          None,
          authorization,
          token,
          userInfo,
          None,
          Some(endSession),
          instant,
          subject
        )
        val json = jsonContentOf("/events/realm-updated.json")
        json.as[Event].rightValue shouldEqual expected
      }

      "decode realm deprecated event" in {
        val expected: RealmEvent = RealmDeprecated("nexusdev", 3L, instant, subject)
        val json                 = jsonContentOf("/events/realm-deprecated.json")
        json.as[Event].rightValue shouldEqual expected
      }
    }
  }
}
