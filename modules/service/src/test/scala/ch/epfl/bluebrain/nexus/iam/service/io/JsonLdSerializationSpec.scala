package ch.epfl.bluebrain.nexus.iam.service.io

import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern

import akka.http.scaladsl.model.Uri
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.iam.types.Identity.{Anonymous, AuthenticatedRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.types.IdentityId
import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import ch.epfl.bluebrain.nexus.iam.core.acls.Event._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.iam.service.io.JsonLdSerialization._
import ch.epfl.bluebrain.nexus.service.http.Path._
import io.circe.parser._
import io.circe.{Decoder, Encoder}
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpecLike}

class JsonLdSerializationSpec extends WordSpecLike with Matchers with TableDrivenPropertyChecks with Resources {
  val apiUri: Uri                    = Uri("http://localhost/prefix")
  implicit val evEnc: Encoder[Event] = eventEncoder(apiUri)
  implicit val evDec: Decoder[Event] = eventDecoder
  private val m                      = jsonLdMarshaller[Event](apiUri)
  private val path                   = "foo" / "bar" / UUID.randomUUID.toString
  private val realm                  = "realm"
  private val user                   = UserRef(realm, "alice")
  private val userExpanded           = user.copy(id = IdentityId(s"$apiUri/${user.id.show}"))
  private val auth                   = AuthenticatedRef(Some("realm"))
  private val authExpanded           = auth.copy(id = IdentityId(s"$apiUri/${auth.id.show}"))
  private val anonymous              = Anonymous()
  private val anonymousExpanded      = anonymous.copy(id = IdentityId(s"$apiUri/${anonymous.id.show}"))
  private val replacements           = Map(Pattern.quote("{{path}}") -> path.toString)

  private val meta         = Meta(user, Instant.ofEpochMilli(1))
  private val metaExpanded = Meta(userExpanded, Instant.ofEpochMilli(1))
  private val permissions  = Permissions(Own, Read, Write)

  val results = Table(
    ("event", "eventExpanded", "json"),
    (
      PermissionsSubtracted(path, user, permissions, meta),
      PermissionsSubtracted(path, userExpanded, permissions, metaExpanded),
      jsonContentOf("/jsonLd/permissions_subtracted.json", replacements)
    ),
    (
      PermissionsAdded(path, AccessControlList(anonymous         -> permissions), meta),
      PermissionsAdded(path, AccessControlList(anonymousExpanded -> permissions), metaExpanded),
      jsonContentOf("/jsonLd/permissions_added.json", replacements)
    ),
    (
      PermissionsRemoved(path, auth, meta),
      PermissionsRemoved(path, authExpanded, metaExpanded),
      jsonContentOf("/jsonLd/permissions_removed.json", replacements)
    ),
    (
      PermissionsCleared(path, meta),
      PermissionsCleared(path, metaExpanded),
      jsonContentOf("/jsonLd/permissions_cleared.json", replacements)
    )
  )

  "EventJsonLdEncoder" should {
    "encoder events to JSON" in {
      forAll(results) { (event, _, json) =>
        parse(m(event)).toOption.get shouldBe json

      }
    }
  }
  "EventJsonLdDecoder" should {
    "decode events from JSON" in {
      forAll(results) { (_, event, json) =>
        json.as[Event] shouldEqual Right(event)
      }
    }
  }
}
