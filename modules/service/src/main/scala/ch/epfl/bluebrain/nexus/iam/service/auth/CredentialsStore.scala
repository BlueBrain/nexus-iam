package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.PublicKey

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.service.auth.CredentialsStoreActor.Protocol.{FetchKey, RefreshCredentials}
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenValidationFailure.{
  KidOrIssuerNotFound,
  UnexpectedErrorPublicKeyRetrieval
}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.{OidcConfig, OidcProviderConfig}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * An JWK store management implementation backed by akka cluster singleton actor
  *
  * @param name   the name given to the actor
  * @param ref    the actor that performs the underlying requests
  * @param logger the logger used to track messages
  * @param tm     the implcitly available timeout
  */
class CredentialsStore(name: String, ref: ActorRef, logger: LoggingAdapter)(implicit tm: Timeout, ec: ExecutionContext)
    extends Serializable {

  /**
    * Retrieves a [[PublicKey]] for the provided [[TokenId]]
    *
    * @param id the [[TokenId]] for which we need to obtain the [[TokenId]]
    */
  def fetchKey(id: TokenId): Future[PublicKey] =
    (ref ? FetchKey(id)) flatMap {
      case key: PublicKey =>
        Future.successful(key)
      // $COVERAGE-OFF$
      case KidOrIssuerNotFound =>
        Future.failed(KidOrIssuerNotFound)
      case unexpected =>
        logger.error("Received an unexpected reply '{}' while fetching the current state of '{}-{}'",
                     unexpected,
                     name,
                     id)
        Future.failed(UnexpectedErrorPublicKeyRetrieval(s"Retrieved an unexpected reply from the actor '$unexpected'"))
    } recoverWith {
      case _: AskTimeoutException =>
        logger.error("Timed out while fetching the key for '{}-{}'", name, id)
        Future.failed(UnexpectedErrorPublicKeyRetrieval(s"timeout while fetching key for id '$id'"))
      case NonFatal(KidOrIssuerNotFound) =>
        Future.failed(KidOrIssuerNotFound: TokenValidationFailure)
      case NonFatal(th) =>
        logger.error(th, "Unexpected exception while fetching the current state of '{}-{}'", name, id)
        Future.failed(UnexpectedErrorPublicKeyRetrieval("Unknown error"))
      // $COVERAGE-ON$
    }

  /**
    * Forces to refresh the credentials
    *
    * @param provider the OIDC provider configuration
    */
  // $COVERAGE-OFF$
  def refreshCredentials(provider: OidcProviderConfig): Unit = {
    val _ = ref ! RefreshCredentials(provider)
  }
  // $COVERAGE-ON$

}

object CredentialsStore {

  /**
    * Initialize the store management implementation backed by akka cluster singleton actor.
    *
    * @param name the name of the underlying action
    */
  final def apply(name: String)(implicit as: ActorSystem,
                                oidcConfig: OidcConfig,
                                ucl: UntypedHttpClient[Future],
                                tm: Timeout,
                                ec: ExecutionContext): CredentialsStore = {
    val ref    = CredentialsStoreActor.start(name)
    val logger = Logging(as, s"CredentialsStore($name)")
    new CredentialsStore(name, ref, logger)
  }
}
