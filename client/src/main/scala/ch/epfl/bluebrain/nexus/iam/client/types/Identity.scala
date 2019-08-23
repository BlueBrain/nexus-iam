package ch.epfl.bluebrain.nexus.iam.client.types

import cats.implicits._
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.config.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.instances._
import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._

/**
  * Base enumeration type for identity classes.
  */
sealed trait Identity extends Product with Serializable {
  def id(implicit config: IamClientConfig): AbsoluteIri
}

object Identity {

  private val allowedInput = "([^/]*)"

  /**
    * Attempt to create a [[Identity]] from its id representation
    *
    * @param id the id
    * @return Some(identity) when the id maps to a known identity pattern, None otherwise
    */
  def apply(id: AbsoluteIri): Option[Identity] = {
    val regexUser      = s".+/v1/realms/$allowedInput/users/$allowedInput".r
    val regexGroup     = s".+/v1/realms/$allowedInput/groups/$allowedInput".r
    val regexAuth      = s".+/v1/realms/$allowedInput/authenticated".r
    val regexAnonymous = ".+/v1/anonymous".r
    id.asString match {
      case regexUser(realm, subject) => Some(User(subject, realm))
      case regexGroup(realm, group)  => Some(Group(group, realm))
      case regexAuth(realm)          => Some(Authenticated(realm))
      case regexAnonymous()          => Some(Anonymous)
      case _                         => None
    }
  }

  /**
    * Base enumeration type for subject classes.
    */
  sealed trait Subject extends Identity

  sealed trait Anonymous extends Subject

  /**
    * The Anonymous subject
    */
  final case object Anonymous extends Anonymous {
    def id(implicit config: IamClientConfig): AbsoluteIri =
      config.publicIri + "anonymous"
  }

  /**
    * The User subject
    *
    * @param subject unique user name
    * @param realm   user realm
    */
  final case class User(subject: String, realm: String) extends Subject {
    def id(implicit config: IamClientConfig): AbsoluteIri =
      config.publicIri + ("realms" / realm / "users" / subject)

  }

  /**
    * The Group identity
    *
    * @param group the group
    * @param realm group realm
    */
  final case class Group(group: String, realm: String) extends Identity {
    def id(implicit config: IamClientConfig): AbsoluteIri =
      config.publicIri + ("realms" / realm / "groups" / group)
  }

  /**
    * The Authenticated identity
    *
    * @param realm the realm
    */
  final case class Authenticated(realm: String) extends Identity {
    def id(implicit config: IamClientConfig): AbsoluteIri =
      config.publicIri + ("realms" / realm / "authenticated")
  }

  implicit def identityEncoder(implicit config: IamClientConfig): Encoder[Identity] = {
    case i @ User(subject, realm) =>
      Json.obj(
        "@id"              -> i.id.asJson,
        "@type"            -> Json.fromString("User"),
        nxv.realm.prefix   -> Json.fromString(realm),
        nxv.subject.prefix -> Json.fromString(subject)
      )
    case i @ Group(group, realm) =>
      Json.obj(
        "@id"            -> i.id.asJson,
        "@type"          -> Json.fromString("Group"),
        nxv.realm.prefix -> Json.fromString(realm),
        nxv.group.prefix -> Json.fromString(group)
      )
    case i @ Authenticated(realm) =>
      Json.obj(
        "@id"            -> i.id.asJson,
        "@type"          -> Json.fromString("Authenticated"),
        nxv.realm.prefix -> Json.fromString(realm)
      )
    case i @ Anonymous =>
      Json.obj("@id" -> i.id.asJson, "@type" -> Json.fromString("Anonymous"))
  }

  private def decodeAnonymous(hc: HCursor): Result[Subject] =
    hc.get[String]("@type").flatMap {
      case "Anonymous" => Right(Anonymous)
      case _           => Left(DecodingFailure("Cannot decode Anonymous Identity", hc.history))
    }

  private def decodeUser(hc: HCursor): Result[Subject] =
    (hc.get[String]("subject"), hc.get[String]("realm")).mapN {
      case (subject, realm) => User(subject, realm)
    }

  private def decodeGroup(hc: HCursor): Result[Identity] =
    (hc.get[String]("group"), hc.get[String]("realm")).mapN {
      case (group, realm) => Group(group, realm)
    }

  private def decodeAuthenticated(hc: HCursor): Result[Identity] =
    hc.get[String]("realm").map(Authenticated)

  private val attempts =
    List[HCursor => Result[Identity]](decodeAnonymous, decodeUser, decodeGroup, decodeAuthenticated)
  private val attemptsSubject = List[HCursor => Result[Subject]](decodeAnonymous, decodeUser)

  implicit val identityDecoder: Decoder[Identity] = {
    Decoder.instance { hc =>
      attempts.foldLeft(Left(DecodingFailure("Unexpected", hc.history)): Result[Identity]) {
        case (acc @ Right(_), _) => acc
        case (_, f)              => f(hc)
      }
    }
  }

  implicit def subjectEncoder(implicit config: IamClientConfig): Encoder[Subject] = Encoder.encodeJson.contramap {
    identityEncoder.apply(_: Identity)
  }

  implicit val subjectDecoder: Decoder[Subject] = Decoder.instance { hc =>
    attemptsSubject.foldLeft(Left(DecodingFailure("Unexpected", hc.history)): Result[Subject]) {
      case (acc @ Right(_), _) => acc
      case (_, f)              => f(hc)
    }
  }
}
