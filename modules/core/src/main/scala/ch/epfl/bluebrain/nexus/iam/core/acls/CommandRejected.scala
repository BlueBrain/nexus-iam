package ch.epfl.bluebrain.nexus.iam.core.acls

import ch.epfl.bluebrain.nexus.common.types.Err

/**
  * Signals the rejection of a command by the persistence layer.
  *
  * @param rejection a descriptive rejection type
  */
@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
final case class CommandRejected(rejection: CommandRejection) extends Err("Command rejected")
