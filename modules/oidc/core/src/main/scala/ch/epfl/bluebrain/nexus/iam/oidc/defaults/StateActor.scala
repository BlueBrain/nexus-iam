package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.iam.oidc.config.Settings
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.StateActor.Protocol._

/**
  * Actor implementation that maintains the state generated during an OIDC authentication server flow.
  */
class StateActor extends Actor with Passivation with ActorLogging {

  def receive: Receive = {
    case GenState(uuid, redirectTo: Option[Uri]) =>
      val state = AuthState(uuid, redirectTo)
      sender() ! state
      context.become(withState(state))
    case ValidateState(uuid) =>
      sender() ! InvalidStateReference(uuid)
      initiateStop()
  }

  def withState(state: AuthState): Receive = {
    case ValidateState(uuid) =>
      if (state.uuid == uuid) {
        sender() ! state
        initiateStop()
      } else sender() ! InvalidStateReference(uuid)
  }

}

object StateActor {

  sealed trait Protocol extends Product with Serializable {
    def uuid: String
  }
  object Protocol {
    final case class GenState(uuid: String, redirectTo: Option[Uri])  extends Protocol
    final case class AuthState(uuid: String, redirectTo: Option[Uri]) extends Protocol
    final case class ValidateState(uuid: String)                      extends Protocol
    final case class InvalidStateReference(uuid: String)              extends Protocol
  }

  // $COVERAGE-OFF$
  final def props: Props = Props[StateActor]()

  private def extractShardId(shards: Int): ExtractShardId = {
    case p: Protocol => math.abs(p.uuid.hashCode) % shards toString
  }

  private def extractEntityId: ExtractEntityId = {
    case p: Protocol => (p.uuid, p)
  }

  final def apply()(implicit as: ActorSystem): ActorRef = {
    val shards = Settings(as).appConfig.cluster.shards
    ClusterSharding(as).start("state", props, ClusterShardingSettings(as), extractEntityId, extractShardId(shards))
  }
  // $COVERAGE-ON$
}
