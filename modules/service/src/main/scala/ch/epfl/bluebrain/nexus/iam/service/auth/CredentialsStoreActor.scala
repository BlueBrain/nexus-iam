package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.PublicKey

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.singleton.{
  ClusterSingletonManager,
  ClusterSingletonManagerSettings,
  ClusterSingletonProxy,
  ClusterSingletonProxySettings
}
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.service.stream.SingletonStreamCoordinator.Stop
import ch.epfl.bluebrain.nexus.iam.service.auth.CredentialsStoreActor.Protocol._
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenValidationFailure.KidOrIssuerNotFound
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.{OidcConfig, OidcProviderConfig}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Actor implementation that maintains the keys of the OIDC providers defined on configuration.
  * Those keys are going to be used to verify the JWT signature.
  */
class CredentialsStoreActor(providers: List[OidcProviderConfig])(implicit ucl: UntypedHttpClient[Future])
    extends Actor
    with ActorLogging {

  private implicit val as: ActorSystem       = context.system
  private implicit val ec: ExecutionContext  = context.dispatcher
  private implicit val mt: ActorMaterializer = ActorMaterializer()
  private var keys: Map[TokenId, PublicKey]  = Map()

  override def preStart(): Unit = updateKeys(providers)

  def receive: Receive = {
    case FetchKey(id) =>
      val requester = sender()
      val _ = keys.get(id) match {
        case Some(key) =>
          requester ! key
        case None =>
          requester ! Failure(KidOrIssuerNotFound)
          log.warning("key for id '{}' not found on the provided keys", id)
          updateKeys(providers)
      }

    case RefreshCredentials(provider) =>
      updateKeys(List(provider))
    // $COVERAGE-OFF$
    case Stop =>
      log.info("Received stop signal, stopping")
      context.stop(self)
    // $COVERAGE-ON$
  }

  private def updateKeys(provs: List[OidcProviderConfig]): Unit =
    provs
      .foreach { provider =>
        JwkClient(provider)
          .map { key =>
            log.info("key for the provider '{}' has been retrieved from {}", provider.issuer, provider.jwkCert)
            keys = keys ++ key
          }
          .recover {
            case _ =>
              log.warning("key for the provider '{}' has failed to be retrieved from {}",
                          provider.issuer,
                          provider.jwkCert)
          }
      }
}

object CredentialsStoreActor {

  sealed trait Protocol extends Product with Serializable

  object Protocol {

    final case class FetchKey(id: TokenId) extends Protocol

    final case class RefreshCredentials(provider: OidcProviderConfig) extends Protocol

    final case object Stop extends Protocol

  }

  // $COVERAGE-OFF$
  final def props(providers: List[OidcProviderConfig])(implicit as: ActorSystem,
                                                       ucl: UntypedHttpClient[Future]): Props =
    ClusterSingletonManager.props(Props(new CredentialsStoreActor(providers)),
                                  terminationMessage = Stop,
                                  settings = ClusterSingletonManagerSettings(as))

  /**
    * Instantiates an actor that maintains the keys of the OIDC providers defined on configuration.
    *
    * @param name the name of the actor
    */
  final def start(
      name: String)(implicit as: ActorSystem, oidc: OidcConfig, ucl: UntypedHttpClient[Future]): ActorRef = {
    val singletonManager = as.actorOf(props(oidc.providers), name)
    as.actorOf(
      ClusterSingletonProxy.props(singletonManagerPath = singletonManager.path.toStringWithoutAddress,
                                  settings = ClusterSingletonProxySettings(as)),
      name = s"${name}Proxy"
    )
  }

  // $COVERAGE-ON$
}
