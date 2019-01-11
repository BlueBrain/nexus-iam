package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import io.circe.{Decoder, DecodingFailure}

/**
  * The client caller. It contains the subject and the list of identities (which contains the subject again)
  *
  * @param subject    the identity that performed the call
  * @param identities the set of other identities associated to the ''subject''. E.g.: groups, anonymous, authenticated
  */
final case class Caller(subject: Subject, identities: Set[Identity])

object Caller {

  /**
    * An anonymous caller
    */
  val anonymous: Caller = Caller(Anonymous: Subject, Set[Identity](Anonymous))

  final implicit val callerDecoder: Decoder[Caller] =
    Decoder.instance { cursor =>
      cursor
        .get[Set[Identity]]("identities")
        .flatMap { identities =>
          identities.find(_.isInstanceOf[Subject]) match {
            case Some(subject: Subject) => Right(Caller(subject, identities))
            case _ =>
              val pos = cursor.downField("identities").history
              Left(DecodingFailure("Unable to find a subject in the collection of identities", pos))
          }
        }
    }
}
