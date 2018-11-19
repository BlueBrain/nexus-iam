package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.iam.types.{AuthToken, Caller}

class Realms[F[_]] {

  /**
    * Attempts to compute the token from the given [[AuthToken]]
    *
    * @param token the provided token
    * @return Some(caller) when the token is valid and a [[Caller]] can be linked to it,
    *         None otherwise. The result is wrapped on an ''F'' effect type.
    */
  def caller(token: AuthToken): F[Option[Caller]] = ???

}
