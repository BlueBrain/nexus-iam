package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import ch.epfl.bluebrain.nexus.iam.oidc.api.UserInfo
import ch.epfl.bluebrain.nexus.iam.oidc.config.Settings
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.UserInfoActor.Protocol._

/**
  * Actor implementation that maintains the user information retrieved during an OIDC authentication server flow.
  */
class UserInfoActor extends Actor with Passivation with ActorLogging {

  def receive: Receive = {
    case SetInfo(_, info) =>
      context.become(withInfo(info))
    case GetInfo(token) =>
      sender() ! Info(token, None)
      initiateStop()
  }

  def withInfo(userInfo: UserInfo): Receive = {
    case GetInfo(token) =>
      sender() ! Info(token, Some(userInfo))
  }
}

object UserInfoActor {

  sealed trait Protocol extends Product with Serializable {
    def token: String
  }

  object Protocol {
    final case class SetInfo(token: String, userInfo: UserInfo)      extends Protocol
    final case class GetInfo(token: String)                          extends Protocol
    final case class Info(token: String, userInfo: Option[UserInfo]) extends Protocol
  }

  // $COVERAGE-OFF$
  final def props: Props = Props[UserInfoActor]()

  private def extractShardId(shards: Int): ExtractShardId = {
    case p: Protocol => math.abs(p.token.hashCode) % shards toString
  }

  private def extractEntityId: ExtractEntityId = {
    case p: Protocol => (p.token.substring(0, 16), p)
  }

  final def apply()(implicit as: ActorSystem): ActorRef = {
    val shards = Settings(as).appConfig.cluster.shards
    ClusterSharding(as).start("userinfo", props, ClusterShardingSettings(as), extractEntityId, extractShardId(shards))
  }
  // $COVERAGE-ON$
}
