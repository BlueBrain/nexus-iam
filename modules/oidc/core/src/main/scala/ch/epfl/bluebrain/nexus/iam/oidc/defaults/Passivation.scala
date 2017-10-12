package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import akka.actor.{Actor, PoisonPill, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.Passivate
import ch.epfl.bluebrain.nexus.iam.oidc.config.Settings

import scala.concurrent.duration.Duration

/**
  * General purpose mixin for cluster sharding actors to gracefully shutdown if they receive no message for the
  * duration of the [[ch.epfl.bluebrain.nexus.iam.oidc.config.AppConfig.ClusterConfig#passivationTimeout]].
  */
// $COVERAGE-OFF$
trait Passivation extends Actor {

  /**
    * @return the maximum tolerated inactivity period after which the actor will be terminated
    */
  protected def passivationTimeout: Duration =
    Settings(context.system).appConfig.cluster.passivationTimeout

  /**
    * Initiates the termination of the actor.
    */
  protected def initiateStop(): Unit =
    context.parent ! Passivate(stopMessage = PoisonPill)

  override def preStart(): Unit = {
    super.preStart()
    // automatically set the receive timeout before the actor is ready to receive messages
    context.setReceiveTimeout(passivationTimeout)
  }

  override def unhandled(msg: Any): Unit = msg match {
    // in case of a `ReceiveTimeout` ask the parent to stop sending messages to this incarnation
    case ReceiveTimeout => initiateStop()
    case _              => super.unhandled(msg)
  }
}
