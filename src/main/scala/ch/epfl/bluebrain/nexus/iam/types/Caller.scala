package ch.epfl.bluebrain.nexus.iam.types

import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.types.Identity.{Anonymous, Subject}
import io.circe.{Encoder, Json}

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

  object JsonLd {
    final implicit def callerEncoder(
        implicit
        S: Encoder[Subject],
        I: Encoder[Identity],
        http: HttpConfig
    ): Encoder[Caller] = {
      import ch.epfl.bluebrain.nexus.iam.config.Contexts.{iamCtxUri, resourceCtxUri}
      import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._

      Encoder.instance[Caller] { caller =>
        Json
          .obj(
            "subject"    -> S(caller.subject),
            "identities" -> Encoder.encodeList(I)(caller.identities.toList.sortBy(_.id.asUri))
          )
          .addContext(iamCtxUri)
          .addContext(resourceCtxUri)
      }
    }
  }
}
