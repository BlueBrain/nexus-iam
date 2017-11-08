package ch.epfl.bluebrain.nexus.iam.service.io

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event

/**
  * A tagging event adapter that adds tags to discriminate between event hierarchies.
  */
class TaggingAdapter extends WriteEventAdapter {

  override def manifest(event: Any): String = ""

  override def toJournal(event: Any): Any = event match {
    case ev: Event => Tagged(ev, Set(TaggingAdapter.tag))
    case _         => event
  }
}

object TaggingAdapter {

  val tag: String = "permission"
}
