package ch.epfl.bluebrain.nexus.iam

import cats.MonadError

package object types {

  /**
    * A resource metadata.
    */
  type ResourceMetadata = ResourceF[Unit]

  /**
    * A MonadError[F, Throwable] for arbitrary ''F[_]'' types.
    */
  type MonadThrowable[F[_]] = MonadError[F, Throwable]

}
