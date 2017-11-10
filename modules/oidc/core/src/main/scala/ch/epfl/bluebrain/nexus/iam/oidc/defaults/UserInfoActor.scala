package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
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

  private[defaults] val sha256: String => String = {
    val digest = MessageDigest.getInstance("SHA-256")

    (string: String) =>
      String.format("%064x", new BigInteger(1, digest.digest(string.getBytes(StandardCharsets.UTF_8))))
  }

  // $COVERAGE-OFF$
  final def props: Props = Props[UserInfoActor]()

  private def extractShardId(shards: Int): ExtractShardId = {
    case p: Protocol => math.abs(p.token.hashCode) % shards toString
  }

  private def extractEntityId: ExtractEntityId = {
    case p: Protocol => (sha256(p.token), p)
  }

  final def apply()(implicit as: ActorSystem): ActorRef = {
    val shards = Settings(as).appConfig.cluster.shards
    ClusterSharding(as).start("userinfo", props, ClusterShardingSettings(as), extractEntityId, extractShardId(shards))
  }
  // $COVERAGE-ON$
}
