package ch.epfl.bluebrain.nexus.iam.core.acls

import java.time.Clock

import ch.epfl.bluebrain.nexus.commons.iam.acls.Meta
import ch.epfl.bluebrain.nexus.commons.iam.auth.User
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{AuthenticatedRef, UserRef}

/**
  * Context information for any operation bundle call
  *
  * @param clock the clock used to issue instants
  * @param user  the author of the operation call
  */
final case class CallerCtx(clock: Clock, user: User) {
  lazy val meta = Meta(identity(), clock.instant())

  private def identity(): Identity =
    user.identities.collectFirst {
      case id: UserRef => id
    } orElse (user.identities.collectFirst {
      case id: AuthenticatedRef => id
    }) getOrElse (Identity.Anonymous)
}

object CallerCtx {

  /**
    * Summons a [[CallerCtx]] instance from the implicit scope.
    *
    * @param instance the implicitly available instance
    */
  implicit final def apply(implicit instance: CallerCtx): CallerCtx = instance

  /**
    * Creates a [[CallerCtx]] from the implicitly available ''clock'' and ''identity''.
    *
    * @param clock the implicitly available clock to issue instants
    * @param user  the implicitly available author
    */
  implicit def fromImplicit(implicit clock: Clock, user: User): CallerCtx = CallerCtx(clock, user)
}
