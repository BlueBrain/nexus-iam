package ch.epfl.bluebrain.nexus.iam.io

import java.time.Instant

import akka.persistence.journal.Tagged
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent.AclDeleted
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsEvent.PermissionsDeleted
import ch.epfl.bluebrain.nexus.iam.realms.RealmEvent.RealmDeprecated
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.Label
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import org.scalatest.{EitherValues, Inspectors, Matchers, WordSpecLike}

class TaggingAdapterSpec extends WordSpecLike with Matchers with Inspectors with EitherValues {

  private val pd = PermissionsDeleted(2L, Instant.EPOCH, Anonymous)
  private val ad = AclDeleted(Path("/a/b/c").right.value, 2L, Instant.EPOCH, Anonymous)
  private val rd = RealmDeprecated(Label.unsafe("blah"), 2L, Instant.EPOCH, Anonymous)

  private val data = Map[AnyRef, (String, AnyRef)](
    pd  -> ("permissions-event" -> Tagged(pd, Set("permissions"))),
    ad  -> ("acl-event"         -> Tagged(ad, Set("acl"))),
    rd  -> ("realm-event"       -> Tagged(rd, Set("realm"))),
    "a" -> (""                  -> "a")
  )

  "A TaggingAdapter" should {
    val adapter = new TaggingAdapter
    "return the correct manifests" in {
      forAll(data.toList) {
        case (event, (manifest, _)) => adapter.manifest(event) shouldEqual manifest
      }
    }
    "return the correct transformed event" in {
      forAll(data.toList) {
        case (event, (_, transformed)) => adapter.toJournal(event) shouldEqual transformed
      }
    }
  }

}
