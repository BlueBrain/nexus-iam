package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.PublicKey

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.service.auth.JwkClient.TokenToPublicKeyError
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcProviderConfig
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class JwkClientSpec
    extends WordSpecLike
    with Matchers
    with MockitoSugar
    with BeforeAndAfter
    with TableDrivenPropertyChecks
    with ScalaFutures {

  implicit val as = ActorSystem("as")
  implicit val ec = as.dispatcher
  implicit val mt = ActorMaterializer()
  implicit val cl = mock[UntypedHttpClient[Future]]

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 100 millis)

  before {
    Mockito.reset(cl)
  }

  "A JwkClient" should {
    val entity = HttpResponse(
      StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        """{"keys":[{"kid":"some","kty":"RSA","alg":"RS256","use":"sig","n":"uy0vZzNASRlm_oqq3agjAO57zwc-R_TEsawOhONzCsSmwTymi5dXsTJl5b2X-G0mdnG3nZmqwXiZRUWeH7nYBkERaTWJqfLKOEryfv4E3R4ykITp8NFc1S5MnzH5eP2T1ZFvCm_TkFp3Xv4V9RzDBbWcSN6ZCFca-QWxqphE5XAcYkMHatqtXQVMHRvciuQ9ujJVHPyle1iuRFAhuzPW0hLg6rzYBMFtOxh_ubA5ckrszkNiWbGUvK0oDnnaWgSxnmsk68_u7mJ2CxTQATj99n5voLKSBQq70L7UbvcxSEkxl5hkGQyfhUu4QyxuBnTiFoU0GcMCMSMjZ3Al8iJk0Q","e":"AQAB"},{"kid":"some2","kty":"RSA","alg":"RS256","use":"sig","n":"uy0vZzNASRlm_oqq3agjAO57zwc-R_TEsawOhONzCsSmwTymi5dXsTJl5b2X-G0mdnG3nZmqwXiZRUWeH7nYBkERaTWJqfLKOEryfv4E3R4ykITp8NFc1S5MnzH5eP2T1ZFvCm_TkFp3Xv4V9RzDBbWcSN6ZCFca-QWxqphE5XAcYkMHatqtXQVMHRvciuQ9ujJVHPyle1iuRFAhuzPW0hLg6rzYBMFtOxh_ubA5ckrszkNiWbGUvK0oDnnaWgSxnmsk68_u7mJ2CxTQATj99n5voLKSBQq70L7UbvcxSEkxl5hkGQyfhUu4QyxuBnTiFoU0GcMCMSMjZ3Al8iJk0Q","e":"AQAB"}]}"""
      )
    )
    val config = OidcProviderConfig(
      "realm",
      "http://localhost:8080/issuer",
      "http://localhost:8080/jwk",
      "http://localhost:8080/oauth2/authorization",
      "http://localhost:8080/oauth2/token",
      "http://localhost:8080/oauth2/userinfo"
    )

    "return a publicKey when the Key ID exists" in {
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(entity))
      val map = JwkClient(config).futureValue
      map.size shouldEqual 2
      map.keySet shouldEqual Set(TokenId(config.issuer, "some"), TokenId(config.issuer, "some2"))
      map(TokenId(config.issuer, "some")) shouldBe a[PublicKey]
    }

    "return TokenToPublicKeyError when one of the JWK n or e are wrong" in {
      val wrongEntity = HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          """{"keys":[{"kid":"some","kty":"RSA","alg":"RS256","use":"sig","n":"uy0vZzNASRlm_oqq3agjAO57zwc-R_TEsawOhONzCsSmwTymi5dXsTJl5b2X-G0mdnG3nZmqwXiZRUWeH7nYBkERaTWJqfLKOEryfv4E3R4ykITp8NFc1S5MnzH5eP2T1ZFvCm_TkFp3Xv4V9RzDBbWcSN6ZCFca-QWxqphE5XAcYkMHatqtXQVMHRvciuQ9ujJVHPyle1iuRFAhuzPW0hLg6rzYBMFtOxh_ubA5ckrszkNiWbGUvK0oDnnaWgSxnmsk68_u7mJ2CxTQATj99n5voLKSBQq70L7UbvcxSEkxl5hkGQyfhUu4QyxuBnTiFoU0GcMCMSMjZ3Al8iJk0Q","e":"AQAB"},{"kid":"some2","kty":"RSA","alg":"RS256","use":"sig","n":"uy0vZzNASRlm_oqq3agjAO57zwc-R_TEsawOhONzCsSmwTymi5dXsTJl5b2X-G0mdnG3nZmqwXiZRUWeH7nYBkERaTWJqfLKOEryfv4E3R4ykITp8NFc1S5MnzH5eP2T1ZFvCm_TkFp3Xv4V9RzDBbWcSN6ZCFca-QWxqphE5XAcYkMHatqtXQVMHRvciuQ9ujJVHPyle1iuRFAhuzPW0hLg6rzYBMFtOxh_ubA5ckrszkNiWbGUvK0oDnnaWgSxnmsk68_u7mJ2CxTQATj99n5voLKSBQq70L7UbvcxSEkxl5hkGQyfhUu4QyxuBnTiFoU0GcMCMSMjZ3Al8iJk0Q","e":"AQAB"},{"kid":"some","kty":"RSA","alg":"RS256","use":"sig","n":"wrong","e":"AQAB"},{"kid":"some","kty":"RSA","alg":"RS256","use":"sig","n":"uy0vZzNASRlm_oqq3agjAO57zwc-R_TEsawOhONzCsSmwTymi5dXsTJl5b2X-G0mdnG3nZmqwXiZRUWeH7nYBkERaTWJqfLKOEryfv4E3R4ykITp8NFc1S5MnzH5eP2T1ZFvCm_TkFp3Xv4V9RzDBbWcSN6ZCFca-QWxqphE5XAcYkMHatqtXQVMHRvciuQ9ujJVHPyle1iuRFAhuzPW0hLg6rzYBMFtOxh_ubA5ckrszkNiWbGUvK0oDnnaWgSxnmsk68_u7mJ2CxTQATj99n5voLKSBQq70L7UbvcxSEkxl5hkGQyfhUu4QyxuBnTiFoU0GcMCMSMjZ3Al8iJk0Q","e":"AQAB"},{"kid":"some2","kty":"RSA","alg":"RS256","use":"sig","n":"uy0vZzNASRlm_oqq3agjAO57zwc-R_TEsawOhONzCsSmwTymi5dXsTJl5b2X-G0mdnG3nZmqwXiZRUWeH7nYBkERaTWJqfLKOEryfv4E3R4ykITp8NFc1S5MnzH5eP2T1ZFvCm_TkFp3Xv4V9RzDBbWcSN6ZCFca-QWxqphE5XAcYkMHatqtXQVMHRvciuQ9ujJVHPyle1iuRFAhuzPW0hLg6rzYBMFtOxh_ubA5ckrszkNiWbGUvK0oDnnaWgSxnmsk68_u7mJ2CxTQATj99n5voLKSBQq70L7UbvcxSEkxl5hkGQyfhUu4QyxuBnTiFoU0GcMCMSMjZ3Al8iJk0Q","e":"AQAB"}]}"""
        )
      )
      when(cl.apply(isA(classOf[HttpRequest])))
        .thenReturn(Future.successful(wrongEntity))
      val f = JwkClient(config)
      ScalaFutures.whenReady(f.failed) { e =>
        e shouldBe a[TokenToPublicKeyError]
      }
    }
  }
}
