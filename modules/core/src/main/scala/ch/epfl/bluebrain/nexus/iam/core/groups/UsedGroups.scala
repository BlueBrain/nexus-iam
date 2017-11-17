package ch.epfl.bluebrain.nexus.iam.core.groups

import cats.MonadError
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.GroupRef
import ch.epfl.bluebrain.nexus.iam.core.groups.UsedGroups.UsedGroupsAggregate
import ch.epfl.bluebrain.nexus.sourcing.Aggregate

/**
  * Actions related to management of user groups used in the service
  * @param agg the underlying aggregate
  * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
  */
final class UsedGroups[F[_]](agg: UsedGroupsAggregate[F])(implicit F: MonadError[F, Throwable]) {

  /**
    * Add group to the list of currently used groups for this group's realm.
    *
    * @param group group to add
    * @return ''Unit'' in ''F[_]'' context
    */
  def add(group: GroupRef): F[Unit] = agg.eval(group.realm, GroupPermissionAddedCommand(group)).map(_ => ())

  /**
    * Fetches the groups currently in use for a realm
    *
    * @param realm realm name
    * @return set of groups for the realm
    */
  def fetch(realm: String): F[Set[GroupRef]] = agg.currentState(realm)

}

object UsedGroups {

  /**
    * Command evaluation logic for used groups. It rejects the command to add groups which already have been added in order
    * to avoid storing too many messages in the event log.
    * @param state groups currently in use
    * @param cmd command wrapping group to be added
    * @return either ''GroupAlreadyAddedRejection'' or ''GroupPermissionAddedEvent''
    */
  final def eval(
      state: Set[GroupRef],
      cmd: GroupPermissionAddedCommand): Either[GroupAlreadyAddedRejection.type, GroupPermissionAddedEvent] = {
    if (state(cmd.groupRef)) Left(GroupAlreadyAddedRejection)
    else Right(GroupPermissionAddedEvent(cmd.groupRef))
  }

  /**
    * State transistion function for used groups. Adds new group to currently used groups for a realm
    * @param state current set of groups
    * @param event event wrapping group to be added
    * @return updated set of groups
    */
  final def next(state: Set[GroupRef], event: GroupPermissionAddedEvent): Set[GroupRef] = {
    state + event.groupRef
  }

  type UsedGroupsAggregate[F[_]] =
    Aggregate.Aux[F,
                  String,
                  GroupPermissionAddedEvent,
                  Set[GroupRef],
                  GroupPermissionAddedCommand,
                  GroupAlreadyAddedRejection.type]

  /**
    * Factory method to create UsedGroups instance
    * @param agg the underlying aggregate
    * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
    * @return new instance of UsedGroups
    */
  def apply[F[_]](agg: UsedGroupsAggregate[F])(implicit F: MonadError[F, Throwable]): UsedGroups[F] =
    new UsedGroups(agg)
}
