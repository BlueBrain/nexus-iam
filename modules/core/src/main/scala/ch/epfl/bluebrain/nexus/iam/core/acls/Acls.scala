package ch.epfl.bluebrain.nexus.iam.core.acls

import cats.MonadError
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.acls._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.acls.Acls.PermissionAggregate
import ch.epfl.bluebrain.nexus.iam.core.acls.Command._
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.State.{Current, Initial}
import ch.epfl.bluebrain.nexus.sourcing.Aggregate
import journal.Logger

/**
  * Unified ACLs actions provided for all the available resources in the service.
  *
  * @param agg   the underlying event log aggregate
  * @tparam F the execution mode of the type class, i.e.: __Try__, __Future__
  */
final class Acls[F[_]](agg: PermissionAggregate[F])(implicit F: MonadError[F, Throwable]) {

  private val log = Logger[this.type]

  /**
    * Fetches the permissions defined on a ''path'' for an ''identity''.
    *
    * @param path     the path
    * @param identity the target identity
    * @return an option containing the permissions associated with this identity for this path, or None if none exists,
    *         in an ''F[_]'' context.
    */
  def fetch(path: Path, identity: Identity): F[Option[Permissions]] = {
    log.debug(s"Fetching permissions for path '${path.show}' and identity '${identity.show}'")
    agg.currentState(path.show).map {
      case Initial             => None
      case Current(_, mapping) => mapping.get(identity)
    }
  }

  /**
    * Fetches the entire permissions mapping for a ''path''.
    *
    * @param path the path
    * @return a map between identities and their respective permissions, in an ''F[_]'' context.
    */
  def fetch(path: Path): F[Map[Identity, Permissions]] = {
    log.debug(s"Fetching all permissions for path '${path.show}'")
    agg.currentState(path.show).map {
      case Initial             => Map.empty
      case Current(_, mapping) => mapping
    }
  }

  /**
    * Removes all permissions on a ''path'' for a specific ''identity''.
    *
    * @param path     the path
    * @param identity the target identity
    * @param ctx      the implicit identity context calling this action
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def remove(path: Path, identity: Identity)(implicit ctx: CallerCtx): F[Unit] = {
    log.debug(s"Removing permissions for path '${path.show}' and identity '${identity.show}'")
    agg.eval(path.show, RemovePermissions(path, identity, ctx.meta)).flatMap {
      case Left(rejection) => F.raiseError(CommandRejected(rejection))
      case Right(Initial) =>
        val th = UnexpectedState[Current, Initial]()
        log.error("Received an unexpected Initial state", th)
        F.raiseError(th)
      case Right(Current(_, _)) =>
        log.debug(s"RemovePermissions succeeded for path '${path.show}' and identity '${identity.show}'")
        F.pure(())
    }
  }

  /**
    * Clears all permissions on a ''path''.
    *
    * @param path the path
    * @param ctx  the implicit identity context calling this action
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def clear(path: Path)(implicit ctx: CallerCtx): F[Unit] = {
    log.debug(s"Clearing all permissions for path '${path.show}'")
    agg.eval(path.show, ClearPermissions(path, ctx.meta)).flatMap {
      case Left(rejection) => F.raiseError(CommandRejected(rejection))
      case Right(Initial) =>
        val th = UnexpectedState[Current, Initial]()
        log.error("Received an unexpected Initial state", th)
        F.raiseError(th)
      case Right(Current(_, _)) =>
        log.debug(s"ClearPermissions succeeded for path '${path.show}'")
        F.pure(())
    }
  }

  /**
    * Creates or appends permissions ''mapping'' on a ''path''
    *
    * @param path the path
    * @param acl  the identity to permissions mapping to create
    * @param ctx  the implicit identity context calling this action
    * @return Unit in an ''F[_]'' context if the action was successful
    */
  def add(path: Path, acl: AccessControlList)(implicit ctx: CallerCtx): F[Unit] = {
    log.debug(s"Creating permissions mapping '$acl' for path '${path.show}'")
    agg.eval(path.show, AddPermissions(path, acl, ctx.meta)).flatMap {
      case Left(rejection) => F.raiseError(CommandRejected(rejection))
      case Right(Initial) =>
        val th = UnexpectedState[Current, Initial]()
        log.error("Received an unexpected Initial state", th)
        F.raiseError(th)
      case Right(Current(_, _)) =>
        log.debug(s"AddPermissions succeeded for path '${path.show}''")
        F.pure(())
    }
  }

  /**
    * Subtracts ''permissions'' on a ''path'' for an ''identity''.
    *
    * @param path        the path
    * @param identity    the target identity
    * @param permissions the permissions to subtract
    * @param ctx         the implicit identity context calling this action
    * @return the resulting permissions in an ''F[_]'' context
    */
  def subtract(path: Path, identity: Identity, permissions: Permissions)(implicit ctx: CallerCtx): F[Permissions] = {
    log.debug(s"Subtracting permissions '$permissions' for path '${path.show}' and identity '${identity.show}'")
    agg.eval(path.show, SubtractPermissions(path, identity, permissions, ctx.meta)).flatMap {
      case Left(rejection) => F.raiseError(CommandRejected(rejection))
      case Right(Initial) =>
        val th = UnexpectedState[Current, Initial]()
        log.error("Received an unexpected Initial state", th)
        F.raiseError(th)
      case Right(Current(_, mapping)) =>
        log.debug(s"SubtractPermissions succeeded for path '${path.show}' and identity '${identity.show}'")
        F.pure(mapping.getOrElse(identity, Permissions.empty))
    }
  }

  /**
    * Retrieves effective permissions for a set of ''identities'' on a ''path'' by combining ACLs on the path
    * and on all its parents.
    *
    * @param path       the target path
    * @param identities the set of identities for which permissions need to be retrieved
    */
  def retrieve(path: Path, identities: Set[Identity]): F[Map[Identity, Permissions]] = {
    val listOfAcls: F[List[Map[Identity, Permissions]]] = path.expand.toList.map(fetch).sequence
    listOfAcls.map(_.foldLeft(Map.empty[Identity, Permissions]) { (acc, el) =>
      el.filterKeys(k => identities.contains(k)).combine(acc)
    })
  }
}

object Acls {

  private def diffPermissions(previous: Map[Identity, Permissions], additional: Map[Identity, Permissions]) =
    additional.foldLeft(Map.empty[Identity, Permissions]) {
      case (acc, (id, perms)) =>
        val diff = perms -- previous.getOrElse(id, Permissions.empty)
        if (diff.nonEmpty) acc + (id -> diff)
        else acc
    }

  type PermissionAggregate[F[_]] = Aggregate.Aux[F, String, Event, State, Command, CommandRejection]

  /**
    * The initial state of an ACL.
    */
  final val initial: State = Initial

  /**
    * Command evaluation logic for ACLs; considering a current ''state'' and a command to be evaluated it either
    * rejects the command or emits a new event that characterizes the change for an aggregate.
    *
    * @param state the current state
    * @param cmd   the command to be evaluated
    * @return either a rejection or emit an event
    */
  final def eval(state: State, cmd: Command): Either[CommandRejection, Event] = {

    def clear(c: ClearPermissions): Either[CommandRejection, Event] = state match {
      case Initial    => Left(CannotClearNonexistentPermissions)
      case _: Current => Right(PermissionsCleared(c.path, c.meta))
    }

    def remove(c: RemovePermissions): Either[CommandRejection, Event] = state match {
      case Current(_, mapping) if mapping.contains(c.identity) => Right(PermissionsRemoved(c.path, c.identity, c.meta))
      case _                                                   => Left(CannotRemoveForNonexistentIdentity)
    }

    def add(c: AddPermissions): Either[CommandRejection, Event] = state match {
      case Initial =>
        if (c.acl.hasVoidPermissions) Left(CannotAddVoidPermissions)
        else Right(PermissionsAdded(c.path, c.acl, c.meta))
      case Current(_, mapping) =>
        if (c.acl.hasVoidPermissions) Left(CannotAddVoidPermissions)
        val newMapping = c.acl.toMap
        val diff       = diffPermissions(mapping, newMapping)
        if (diff.nonEmpty) Right(PermissionsAdded(c.path, AccessControlList.fromMap(diff), c.meta))
        else Left(CannotAddVoidPermissions)
    }

    def subtract(c: SubtractPermissions): Either[CommandRejection, Event] = state match {
      case Initial => Left(CannotSubtractFromNonexistentPermissions)
      case Current(_, mapping) =>
        mapping.get(c.identity) match {
          case Some(existing) =>
            val intersection = c.permissions & existing
            if (intersection.isEmpty) Left(CannotSubtractVoidPermissions)
            else Right(PermissionsSubtracted(c.path, c.identity, intersection, c.meta))
          case None => Left(CannotSubtractForNonexistentIdentity)
        }
    }

    cmd match {
      case c: ClearPermissions    => clear(c)
      case c: RemovePermissions   => remove(c)
      case c: AddPermissions      => add(c)
      case c: SubtractPermissions => subtract(c)
    }
  }

  /**
    * State transition function for ACLs; considering a current state (the ''state'' argument) and an emitted
    * ''event'' it computes the next state.
    *
    * @param state the current state
    * @param event the emitted event
    * @return the next state
    */
  final def next(state: State, event: Event): State = (state, event) match {
    case (Initial, PermissionsAdded(path, acl, _))              => Current(path, acl.toMap)
    case (Initial, _: Event)                                    => Initial
    case (_: Current, PermissionsCleared(path, _))              => Current(path, Map.empty)
    case (Current(_, mapping), PermissionsRemoved(path, id, _)) => Current(path, mapping - id)
    case (Current(_, mapping), PermissionsAdded(path, acl, _))  => Current(path, mapping |+| acl.toMap)
    case (Current(_, mapping), PermissionsSubtracted(path, id, perms, _)) =>
      Current(path, mapping.updated(id, mapping(id) -- perms))
  }

  /**
    * Factory method to create an Acls instance.
    *
    * @see [[ch.epfl.bluebrain.nexus.iam.core.acls.Acls]]
    */
  @inline
  def apply[F[_]](agg: PermissionAggregate[F])(implicit F: MonadError[F, Throwable]): Acls[F] =
    new Acls[F](agg)(F)

}
