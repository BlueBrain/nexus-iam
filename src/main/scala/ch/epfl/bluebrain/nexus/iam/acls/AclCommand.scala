package ch.epfl.bluebrain.nexus.iam.acls

import java.time.Instant

import cats.effect.Async
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent._
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.AclState.{Current, Initial}
import ch.epfl.bluebrain.nexus.service.http.Path

/**
  * Enumeration of ACL collection command types.
  */
sealed trait AclCommand extends Product with Serializable {

  /**
    * @return the target path for the ACL
    */
  def path: Path

  /**
    * @return the instant when this command was created
    */
  def instant: Instant

  /**
    * @return the identity which created this command
    */
  def identity: Identity

}

object AclCommand {

  /**
    * An intent to create ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be created, represented as a mapping of identities to permissions
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class CreateAcl(path: Path, acl: AccessControlList, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to update ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be updated, represented as a mapping of identities to permissions
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class UpdateAcl(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to append ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be appended, represented as a mapping of identities to permissions
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class AppendAcl(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to subtract ACL.
    *
    * @param path     the target path for the ACL
    * @param acl      the ACL to be subtracted, represented as a mapping of identities to permissions
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class SubtractAcl(path: Path, acl: AccessControlList, rev: Long, instant: Instant, identity: Identity)
      extends AclCommand

  /**
    * An intent to delete ACL.
    *
    * @param path     the target path for the ACL
    * @param rev      the last known revision of the resource when this command was created
    * @param instant  the instant when this command was created
    * @param identity the identity which created this command
    */
  final case class DeleteAcl(path: Path, rev: Long, instant: Instant, identity: Identity) extends AclCommand

  def evaluate[F[_]](state: AclState, command: AclCommand)(implicit F: Async[F]): F[Either[AclRejection, AclEvent]] = {

    def create(c: CreateAcl): Either[AclRejection, AclEvent] = state match {
      case Initial if c.acl.hasVoidPermissions => Left(AclInvalidEmptyPermissions(c.path))
      case Initial                             => Right(AclCreated(c.path, c.acl, 1L, c.instant, c.identity))
      case _: Current                          => Left(AclAlreadyExists(c.path))
    }

    def update(c: UpdateAcl): Either[AclRejection, AclEvent] = state match {
      case Initial                                => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1     => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions => Left(AclInvalidEmptyPermissions(c.path))
      case _: Current                             => Right(AclUpdated(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def append(c: AppendAcl): Either[AclRejection, AclEvent] = state match {
      case Initial                                  => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1       => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions   => Left(AclInvalidEmptyPermissions(c.path))
      case ss: Current if ss.acl ++ c.acl == ss.acl => Left(AclAlreadyExists(c.path))
      case _: Current                               => Right(AclAppended(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def subtract(c: SubtractAcl): Either[AclRejection, AclEvent] = state match {
      case Initial                                  => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1       => Left(AclIncorrectRev(c.path, c.rev))
      case _: Current if c.acl.hasVoidPermissions   => Left(AclInvalidEmptyPermissions(c.path))
      case ss: Current if ss.acl -- c.acl == ss.acl => Left(AclAlreadyExists(c.path))
      case _: Current                               => Right(AclSubtracted(c.path, c.acl, c.rev, c.instant, c.identity))
    }

    def delete(c: DeleteAcl): Either[AclRejection, AclEvent] = state match {
      case Initial                                          => Left(AclNotFound(c.path))
      case ss: Current if c.rev != ss.rev + 1               => Left(AclIncorrectRev(c.path, c.rev))
      case ss: Current if ss.acl == AccessControlList.empty => Left(AclIsEmpty(c.path))
      case _: Current                                       => Right(AclDeleted(c.path, c.rev, c.instant, c.identity))
    }

    command match {
      case c: CreateAcl   => F.pure(create(c))
      case c: UpdateAcl   => F.pure(update(c))
      case c: AppendAcl   => F.pure(append(c))
      case c: SubtractAcl => F.pure(subtract(c))
      case c: DeleteAcl   => F.pure(delete(c))
    }
  }
}
