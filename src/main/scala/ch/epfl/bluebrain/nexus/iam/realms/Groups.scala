package ch.epfl.bluebrain.nexus.iam.realms

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props, ReceiveTimeout}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId, Passivate}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import cats.effect.{Async, IO, Timer}
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.http.{HttpClient, UnexpectedUnsuccessfulHttpResponse}
import ch.epfl.bluebrain.nexus.iam.auth.{AccessToken, TokenRejection}
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.GroupsConfig
import ch.epfl.bluebrain.nexus.iam.realms.Groups._
import ch.epfl.bluebrain.nexus.iam.types.IamError.{InternalError, OperationTimedOut}
import ch.epfl.bluebrain.nexus.iam.types.Identity.Group
import ch.epfl.bluebrain.nexus.iam.types.{IamError, Label}
import ch.epfl.bluebrain.nexus.sourcing.retry.Retry
import ch.epfl.bluebrain.nexus.sourcing.retry.syntax._
import com.nimbusds.jwt.JWTClaimsSet
import io.circe.{Decoder, HCursor, Json}
import journal.Logger

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Extracts and caches caller group set using the access token or the realm user info endpoint as sources.
  *
  * @param ref the underlying cluster sharding actor ref proxy
  */
class Groups[F[_]](ref: ActorRef)(
    implicit as: ActorSystem,
    cfg: GroupsConfig,
    hc: HttpClient[F, Json],
    F: Async[F],
    T: Timer[F]
) {

  private[this] val logger = Logger[this.type]

  private implicit val tm: Timeout                = Timeout(cfg.askTimeout)
  private implicit val retry: Retry[F, Throwable] = Retry(cfg.retry.retryStrategy)
  private implicit val contextShift               = IO.contextShift(as.dispatcher)

  /**
    * Returns the caller group set either from the claimset, in cache or from the user info endpoint of the provided
    * realm.
    *
    * @param token     the token to use as key for the group information
    * @param claimsSet the set of claims in the access token
    * @param realm     the realm against which the caller is authenticated
    * @param exp       an optional expiry for the token
    */
  def groups(token: AccessToken, claimsSet: JWTClaimsSet, realm: ActiveRealm, exp: Option[Instant]): F[Set[Group]] = {
    if (claimsSet.getClaims.containsKey("groups")) fromClaimSet(claimsSet, realm.id).pure[F]
    else fromUserInfo(token, realm, exp)
  }

  private def fromClaimSet(claimsSet: JWTClaimsSet, realmId: Label): Set[Group] = {
    import scala.collection.JavaConverters._
    val strings = Try(claimsSet.getStringListClaim("groups").asScala.toList)
      .filter(_ != null)
      .map(_.map(_.trim))
      .map(_.filterNot(_.isEmpty))
      .recoverWith { case _ => Try(claimsSet.getStringClaim("groups").split(",").map(_.trim).toList) }
      .toOption
      .map(_.toSet)
      .getOrElse(Set.empty)
    strings.map(s => Group(s, realmId.value))
  }

  private def fromUserInfo(token: AccessToken, realm: ActiveRealm, exp: Option[Instant]): F[Set[Group]] = {
    fromRef(token).flatMap {
      case Some(set) => F.pure(set)
      case None =>
        for {
          set <- fetch(token, realm).retry
          _   <- toRef(token, set, exp.getOrElse(Instant.now().plusMillis(cfg.passivationTimeout.toMillis)))
        } yield set
    }
  }

  private def fromRef(token: AccessToken): F[Option[Set[Group]]] =
    send(Read(token), (r: Response) => r.groups)

  private def toRef(token: AccessToken, groups: Set[Group], exp: Instant): F[Unit] =
    send(Write(token, groups, exp), (_: Ack) => ())

  private def fetch(token: AccessToken, realm: ActiveRealm): F[Set[Group]] = {
    def fromSet(cursor: HCursor): Decoder.Result[Set[String]] =
      cursor.get[Set[String]]("groups").map(_.map(_.trim).filterNot(_.isEmpty))
    def fromCsv(cursor: HCursor): Decoder.Result[Set[String]] =
      cursor.get[String]("groups").map(_.split(",").map(_.trim).filterNot(_.isEmpty).toSet)

    val req = Get(realm.userInfoEndpoint.asUri).addCredentials(OAuth2BearerToken(token.value))
    hc(req)
      .map { json =>
        val stringGroups = fromSet(json.hcursor) orElse fromCsv(json.hcursor) getOrElse Set.empty[String]
        stringGroups.map(str => Group(str, realm.id.value))
      }
      .recoverWith {
        case UnexpectedUnsuccessfulHttpResponse(resp, body) =>
          if (resp.status == StatusCodes.Unauthorized || resp.status == StatusCodes.Forbidden) {
            logger.warn(s"A provided client token was rejected by the OIDC provider, reason: '$body'")
            F.raiseError(IamError.InvalidAccessToken(TokenRejection.InvalidAccessToken))
          } else {
            logger.warn(
              s"A call to get the groups from the OIDC provider failed unexpectedly, status '${resp.status}', reason: '$body'"
            )
            F.raiseError(IamError.InternalError("Unable to extract group information from the OIDC provider."))
          }
      }
  }

  private def send[Reply, A](msg: Msg, f: Reply => A)(implicit Reply: ClassTag[Reply]): F[A] = {
    val genericError  = InternalError("The system experienced an unexpected error, please try again later.")
    val timedOutError = OperationTimedOut("Timed out while waiting for a reply from the group cache.")
    val future        = IO(ref ? msg)
    val fa            = IO.fromFuture(future).to[F]
    fa.flatMap[A] {
        case Reply(value) => F.pure(f(value))
        case other =>
          logger.error(s"Received unexpected reply from the group cache: '$other'")
          F.raiseError(genericError)
      }
      .recoverWith {
        case _: AskTimeoutException =>
          F.raiseError(timedOutError)
        case NonFatal(th) =>
          logger.error("Exception caught while exchanging messages with the groups cache", th)
          F.raiseError(genericError)
      }
  }
}

object Groups {

  /**
    * Constructs a Groups instance with its underlying cache from the provided implicit args.
    */
  def apply[F[_]: Async: Timer](
      shardingSettings: Option[ClusterShardingSettings] = None
  )(implicit as: ActorSystem, cfg: GroupsConfig, hc: HttpClient[F, Json]): F[Groups[F]] = {
    val settings = shardingSettings.getOrElse(ClusterShardingSettings(as))
    val shardExtractor: ExtractShardId = {
      case msg: Msg => math.abs(msg.token.value.hashCode) % cfg.shards toString
    }
    val entityExtractor: ExtractEntityId = {
      case msg: Msg => (msg.token.value, msg)
    }
    val props = Props(new GroupCache(cfg))
    val F     = implicitly[Async[F]]
    F.delay {
      val ref = ClusterSharding(as).start("groups", props, settings, entityExtractor, shardExtractor)
      new Groups[F](ref)
    }
  }

  sealed trait Msg extends Product with Serializable {
    def token: AccessToken
  }
  final case class Read(token: AccessToken)                                    extends Msg
  final case class Write(token: AccessToken, groups: Set[Group], exp: Instant) extends Msg
  final case class Response(token: AccessToken, groups: Option[Set[Group]])    extends Msg
  final case class Ack(token: AccessToken)                                     extends Msg

  private class GroupCache(cfg: GroupsConfig) extends Actor with ActorLogging {

    //noinspection ActorMutableStateInspection
    private var groups: Option[Set[Group]] = None

    // shutdown actor automatically after the default timeout
    override def preStart(): Unit = {
      super.preStart()
      context.setReceiveTimeout(cfg.passivationTimeout)
    }

    def receive: Receive = {
      case Read(token) =>
        sender() ! Response(token, groups)
      case Write(token, gs, exp) =>
        groups = Some(gs)
        sender() ! Ack(token)
        // passivate with the minimum duration (either the token expiry or the passivation timeout)
        val delta = Math.min(exp.toEpochMilli - Instant.now().toEpochMilli, cfg.passivationTimeout.toMillis)
        if (delta > 1L) {
          context.setReceiveTimeout(Duration(delta, TimeUnit.MILLISECONDS))
        } else {
          context.parent ! Passivate(PoisonPill)
        }
      case ReceiveTimeout =>
        context.parent ! Passivate(PoisonPill)
    }
  }
}
