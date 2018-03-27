package ch.epfl.bluebrain.nexus.iam.oidc.api

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo

/**
  * Contract for OIDC interactions between IAM and an IAM Integration Service.
  * @tparam F
  */
trait OidcOps[F[_]] {

  /**
    * Constructs a redirect uri to be sent to the client for authenticating during the OIDC server flow.
    *
    * @param finalRedirect the final uri where the caller should be redirected at the end of a successful authorization
    *                      flow
    */
  def buildRedirectUri(finalRedirect: Option[Uri]): F[Uri]

  /**
    * Exchanges the client supplied code for an ''IdAccessToken''.  The supplied state needs to be validated and if the
    * client has requested a final redirect during the initialization of the server flow, extract this uri from the
    * state.
    *
    * @param code the code provided by the downstream OIDC provider
    * @param state the state created by the integration service
    */
  def exchangeCode(code: String, state: String): F[(IdAccessToken, Option[Uri])]

  /**
    * Attempts to retrieve the authenticated user information using a valid access token.
    * @param accessToken the access token to use to authenticate to the downstream OIDC provider
    */
  def getUserInfo(accessToken: String): F[UserInfo]

}
