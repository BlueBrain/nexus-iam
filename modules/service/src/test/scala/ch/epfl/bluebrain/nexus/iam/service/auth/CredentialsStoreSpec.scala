package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.PublicKey

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.testkit.{ImplicitSender, TestKit}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenValidationFailure.KidOrIssuerNotFound
import ch.epfl.bluebrain.nexus.iam.service.routes.Fixtures
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class CredentialsStoreSpec
    extends TestKit(ActorSystem("CredentialsStore"))
    with WordSpecLike
    with Matchers
    with Fixtures
    with BeforeAndAfterAll
    with ScalaFutures
    with MockitoSugar
    with Resources
    with ImplicitSender {

  private val cluster = Cluster(system)
  implicit val ec     = system.dispatcher

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cluster.join(cluster.selfAddress)
  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(10 seconds, 1 second)

  private implicit val cl = mock[UntypedHttpClient[Future]]
  private val jwk1        = jsonContentOf("/auth/jwk1.json").noSpaces
  private val jwk2        = jsonContentOf("/auth/jwk2.json").noSpaces

  private val provider1Issuer = oidc.providers(0).issuer

  when(cl.apply(Get(oidc.providers(0).jwkCert)))
    .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, jwk1))))
  when(cl.apply(Get(oidc.providers(1).jwkCert)))
    .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, jwk2))))

  "a CredentialsStoreActor" should {

    val store = CredentialsStore("name")

    "return the correct key when it is present" in {
      store.fetchKey(TokenId(provider1Issuer, "kid1")).futureValue shouldBe a[PublicKey]
    }

    "return KidOrIssuerNotFound when the key is not present" in {
      ScalaFutures.whenReady(store.fetchKey(TokenId("http://localhost:8080/v0/wrong/uri", "kid2")).failed) { e =>
        e shouldBe a[KidOrIssuerNotFound.type]
      }

    }
  }
}
