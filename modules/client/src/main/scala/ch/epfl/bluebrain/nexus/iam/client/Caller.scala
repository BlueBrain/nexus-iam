package ch.epfl.bluebrain.nexus.iam.client

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.Anonymous
import ch.epfl.bluebrain.nexus.commons.types.identity.IdentityId.IdentityIdPrefix
import ch.epfl.bluebrain.nexus.commons.types.identity.{Identity, User}

/**
  * Base enumeration type for caller classes.
  */
sealed trait Caller extends Product with Serializable {

  /**
    * @return the identities this ''caller'' belongs to
    */
  def identities: Set[Identity]

  /**
    * @return the ''credentials'' used by the caller to authenticate
    */
  def credentials: Option[OAuth2BearerToken]
}
object Caller {

  /**
    * An anonymous caller.
    *
    * @param identity the ''anonymous'' [[Identity]] for that caller
    */
  final case class AnonymousCaller(identity: Anonymous) extends Caller {
    override val identities  = Set(identity)
    override val credentials = None
  }
  object AnonymousCaller {

    /**
      * Constructs a [[AnonymousCaller]] form the implicitly available ''iamUri''
      *
      * @param iamUri the implicitly available ''iamUri''
      */
    final def apply()(implicit iamUri: IamUri): AnonymousCaller = apply(iamUri.value)

    /**
      * Constructs a [[AnonymousCaller]] form the provided ''uri''.
      *
      * @param uri the uri to build the [[Anonymous]] caller
      */
    final def apply(uri: Uri): AnonymousCaller = {
      implicit val _ = IdentityIdPrefix(s"$uri")
      AnonymousCaller(Anonymous())
    }
  }

  /**
    * An authenticated caller.
    *
    * @param credentials the identities this ''caller'' belongs to
    * @param identities the ''credentials'' used by the caller to authenticate
    */
  final case class AuthenticatedCaller(credentials: Option[OAuth2BearerToken], identities: Set[Identity]) extends Caller

  object AuthenticatedCaller {

    /**
      * Construct a [[AuthenticatedCaller]] from provided ''credentials'' and ''user''.
      *
      * @param credentials the identities this ''caller'' belongs to
      * @param user       the user information about this caller
      */
    final def apply(credentials: OAuth2BearerToken, user: User): AuthenticatedCaller =
      new AuthenticatedCaller(Some(credentials), user.identities)
  }

  /**
    * Implicit conversion from implicitly available ''available'' to optional [[OAuth2BearerToken]]
    *
    * @param caller the implicitly available caller
    * @return an optional [[OAuth2BearerToken]]
    */
  final implicit def callerToToken(implicit caller: Caller): Option[OAuth2BearerToken] =
    caller.credentials
}
