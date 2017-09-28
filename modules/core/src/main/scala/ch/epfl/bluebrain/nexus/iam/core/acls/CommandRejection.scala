package ch.epfl.bluebrain.nexus.iam.core.acls

/**
  * Enumeration type for ACL command rejections.
  */
sealed trait CommandRejection extends Rejection

object CommandRejection {

  /**
    * Descriptive rejection name when attempting to clear permissions that don't exist.
    */
  final case object CannotClearNonexistentPermissions extends CommandRejection

  /**
    * Descriptive rejection name when attempting to add an empty set or delta of permissions.
    */
  final case object CannotAddVoidPermissions extends CommandRejection

  /**
    * Descriptive rejection name when attempting to subtract an empty set or delta of permissions.
    */
  final case object CannotSubtractVoidPermissions extends CommandRejection

  /**
    * Descriptive rejection name when attempting to subtract all permissions for an identity.
    */
  final case object CannotSubtractAllPermissions extends CommandRejection

  /**
    * Descriptive rejection name when attempting to subtract permissions from a nonexistent mapping.
    */
  final case object CannotSubtractFromNonexistentPermissions extends CommandRejection

  /**
    * Descriptive rejection name when attempting to subtract permissions for a nonexistent identity.
    */
  final case object CannotSubtractForNonexistentIdentity extends CommandRejection

  /**
    * Descriptive rejection name when attempting to remove permissions for a nonexistent identity.
    */
  final case object CannotRemoveForNonexistentIdentity extends CommandRejection

}