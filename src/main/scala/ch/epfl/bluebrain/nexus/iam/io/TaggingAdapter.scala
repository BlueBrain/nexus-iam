package ch.epfl.bluebrain.nexus.iam.io
import akka.persistence.journal.{Tagged, WriteEventAdapter}
import ch.epfl.bluebrain.nexus.iam.acls.AclEvent
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsEvent

/**
  * A tagging event adapter that adds tags to discriminate between event hierarchies.
  */
class TaggingAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = event match {
    case _: PermissionsEvent => "permissions-event"
    case _: AclEvent         => "acl-event"
    case _                   => ""
  }

  override def toJournal(event: Any): Any = event match {
    case ev: PermissionsEvent => Tagged(ev, Set("permissions"))
    case ev: AclEvent         => Tagged(ev, Set("acl"))
    case _                    => event
  }
}
