package ch.epfl.bluebrain.nexus.iam.client.types.events

import cats.implicits._
import ch.epfl.bluebrain.nexus.iam.client.types.AccessControlList.aclEntityDecoder
import ch.epfl.bluebrain.nexus.iam.client.types.GrantType.Camel._
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Subject
import ch.epfl.bluebrain.nexus.iam.client.types.{AccessControlList, Identity, Permission}
import ch.epfl.bluebrain.nexus.rdf.instances._
import ch.epfl.bluebrain.nexus.rdf.Iri
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, DecodingFailure}

object decoders {

  private implicit val config: Configuration = Configuration.default
    .withDiscriminator("@type")
    .copy(transformMemberNames = {
      case "label"                 => "_label"
      case "rev"                   => "_rev"
      case "instant"               => "_instant"
      case "subject"               => "_subject"
      case "path"                  => "_path"
      case "issuer"                => "_issuer"
      case "keys"                  => "_keys"
      case "grantTypes"            => "_grantTypes"
      case "authorizationEndpoint" => "_authorizationEndpoint"
      case "tokenEndpoint"         => "_tokenEndpoint"
      case "userInfoEndpoint"      => "_userInfoEndpoint"
      case "revocationEndpoint"    => "_revocationEndpoint"
      case "endSessionEndpoint"    => "_endSessionEndpoint"
      case other                   => other
    })

  private implicit val subjectDecoder: Decoder[Subject] =
    Decoder.decodeString.flatMap { id =>
      Iri.absolute(id) match {
        case Left(_) => Decoder.failedWithMessage(s"Couldn't convert id '$id' to Absolute Iri")
        case Right(iri) =>
          Identity(iri) match {
            case Some(s: Subject) => Decoder.const(s)
            case _                => Decoder.failedWithMessage(s"Couldn't decode subject from '$id'")
          }
      }
    }

  /**
    * [[Decoder]] for [[Event]]s.
    */
  implicit val eventDecoder: Decoder[Event] =
    deriveDecoder[Event]

  private implicit val aclDecoder: Decoder[AccessControlList] =
    Decoder.instance { hc =>
      for {
        arr <- hc.focus.flatMap(_.asArray).toRight(DecodingFailure("acl field not found", hc.history))
        acl <- arr.foldM(Map.empty[Identity, Set[Permission]]) {
          case (acc, j) => aclEntityDecoder(j.hcursor).map(acc + _)
        }
      } yield AccessControlList(acl)
    }

}
