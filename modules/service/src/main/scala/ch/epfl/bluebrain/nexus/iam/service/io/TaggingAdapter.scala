package ch.epfl.bluebrain.nexus.iam.service.io

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import ch.epfl.bluebrain.nexus.iam.core.acls.Event
import TaggingAdapter.tag

/**
  * A tagging event adapter that adds tags to discriminate between event hierarchies.
  */
class TaggingAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = event match {
    case ev: Event => Tagged(ev, Set(tag))
    case _         => event
  }
}

object TaggingAdapter {

  val tag: String = "permission"
}
