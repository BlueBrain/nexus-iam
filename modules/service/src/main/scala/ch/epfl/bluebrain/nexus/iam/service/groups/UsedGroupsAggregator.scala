package ch.epfl.bluebrain.nexus.iam.service.groups

import cats.MonadError
import cats.implicits._
import ch.epfl.bluebrain.nexus.iam.types.Identity.GroupRef
import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import ch.epfl.bluebrain.nexus.iam.core.acls.Event.PermissionsAdded
import ch.epfl.bluebrain.nexus.iam.core.groups.UsedGroups

/**
  * Class responsible for aggregating used groups
  *
  * @param usedGroups used groups operations
  * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
  */
class UsedGroupsAggregator[F[_]](usedGroups: UsedGroups[F])(implicit F: MonadError[F, Throwable]) {

  final def apply(event: Event): F[Unit] = {
    val groups: Set[GroupRef] = event match {
      case PermissionsAdded(_, acl, _) =>
        acl.acl.map(_.identity).flatMap {
          case g: GroupRef => Set(g)
          case _           => Set.empty[GroupRef]
        }
      case _ => Set.empty[GroupRef]
    }

    groups
      .map(usedGroups.add)
      .reduce((f1, f2) => f1.product(f2).map(_ => ()))
  }

}

object UsedGroupsAggregator {

  /**
    * Factory method for ''UsedGroupsAggregator''
    *
    * @param usedGroups used groups operations
    * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
    * @return new instance of ''UsedGroupsAggregator''
    */
  final def apply[F[_]](usedGroups: UsedGroups[F])(implicit F: MonadError[F, Throwable]): UsedGroupsAggregator[F] =
    new UsedGroupsAggregator[F](usedGroups)
}
