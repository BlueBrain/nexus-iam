package ch.epfl.bluebrain.nexus.iam.realms

import ch.epfl.bluebrain.nexus.iam.types.Label
import ch.epfl.bluebrain.nexus.rdf.Iri.Url

/**
  * Enumeration of realm rejection types.
  *
  * @param msg a descriptive message for why the rejection occurred
  */
sealed abstract class RealmRejection(val msg: String) extends Product with Serializable

object RealmRejection {

  /**
    * Rejection returned when attempting to create a realm with an id that already exists.
    *
    * @param id the id of the realm
    */
  final case class RealmAlreadyExists(id: Label) extends RealmRejection(s"Realm '${id.value}' already exists.")

  /**
    * Rejection returned when attempting to update a realm with an id that doesnt exist.
    *
    * @param id the id of the realm
    */
  final case class RealmNotFound(id: Label) extends RealmRejection(s"Realm '${id.value}' not found.")

  /**
    * Rejection returned when attempting to deprecate a realm that is already deprecated.
    *
    * @param id the id of the realm
    */
  final case class RealmAlreadyDeprecated(id: Label)
      extends RealmRejection(s"Realm '${id.value}' is already deprecated.")

  /**
    * Rejection returned when a subject intends to perform an operation on the current realm, but either provided an
    * incorrect revision or a concurrent update won over this attempt.
    *
    * @param rev the provided revision
    */
  final case class IncorrectRev(rev: Long)
      extends RealmRejection(s"Incorrect revision '$rev' provided, the realm may have been updated since last seen.")

  /**
    * Rejection returned when attempting to parse an openid configuration document, but the grant types are not properly
    * formatted.
    *
    * @param document the address of the document
    * @param location the location in the document
    */
  final case class IllegalGrantTypeFormat(document: Url, location: String)
      extends RealmRejection(
        s"Failed to parse '$location' from '${document.asUri}' as a collection of supported grant types.")

  /**
    * Rejection returned when attempting to parse an openid configuration document, but the issuer is not properly
    * formatted.
    *
    * @param document the address of the document
    * @param location the location in the document
    */
  final case class IllegalIssuerFormat(document: Url, location: String)
      extends RealmRejection(s"Failed to parse '$location' from '${document.asUri}' as an issuer url.")

  /**
    * Rejection returned when attempting to parse an openid configuration document, but the jwks uri is not properly
    * formatted.
    *
    * @param document the address of the document
    * @param location the location in the document
    */
  final case class IllegalJwksUriFormat(document: Url, location: String)
      extends RealmRejection(s"Failed to parse '$location' from '${document.asUri}' as a jwk url.")

  /**
    * Rejection returned when attempting to parse a JWKS document, but it's not properly formatted.
    *
    * @param document the address of the document
    */
  final case class IllegalJwkFormat(document: Url)
      extends RealmRejection(s"Illegal format of the JWKs document '${document.asUri}'.")

  /**
    * Rejection returned when attempting to fetch a JWKS document but the response is not a successful one.
    *
    * @param document the address of the document
    */
  final case class UnsuccessfulJwksResponse(document: Url)
      extends RealmRejection(s"Failed to retrieve the JWKs document '${document.asUri}'.")

  /**
    * Rejection returned when attempting to fetch an openid config document but the response is not a successful one.
    *
    * @param document the address of the document
    */
  final case class UnsuccessfulOpenIdConfigResponse(document: Url)
      extends RealmRejection(s"Failed to retrieve the openid config document '${document.asUri}'.")

  /**
    * Rejection returned when attempting to parse a JWKS document, but no supported keys are found.
    *
    * @param document the address of the document
    */
  final case class NoValidKeysFound(document: Url)
      extends RealmRejection(s"Failed to find a valid RSA JWK key at '${document.asUri}'.")

}
