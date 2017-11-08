package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import scala.util.Try

object Jwk {

  /**
    * Wrapper for a collection of JSON Web Key
    *
    * @param keys collection of keys
    */
  final case class Jwks(keys: List[Jwk])

  /**
    * JSON Web Key from rfc7517
    *
    * @param alg the algorithm parameter
    * @param e   the RSA key value which specifies the ''exponent''
    * @param n   the RSA key value which specifies the ''modulus''
    * @param kty the key type parameter
    * @param kid the KEY ID of the ket
    * @param use the public key use
    */
  final case class Jwk(alg: String, e: String, n: String, kty: String, kid: String, use: Option[String]) {
    /**
      * Attempts to compute the [[PublicKey]] from the ''n'' and ''e'' fields
      */
    lazy val key: Try[PublicKey] = Try {
      val modulus  = BigInt(1, Base64.getUrlDecoder.decode(n))
      val exponent = BigInt(1, Base64.getUrlDecoder.decode(e))
      KeyFactory.getInstance(kty).generatePublic(new RSAPublicKeySpec(modulus.bigInteger, exponent.bigInteger))
    }
  }
}
