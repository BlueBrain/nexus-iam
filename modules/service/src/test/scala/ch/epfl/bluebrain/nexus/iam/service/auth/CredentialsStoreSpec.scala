package ch.epfl.bluebrain.nexus.iam.service.auth

import java.security.PublicKey

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.testkit.{ImplicitSender, TestKit}
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.service.auth.TokenValidationFailure.KidOrIssuerNotFound
import ch.epfl.bluebrain.nexus.iam.service.routes.Fixtures
import org.mockito.Mockito.when
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, Matchers, WordSpecLike}

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
    with ImplicitSender
    with BeforeAndAfter
    with Eventually {

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

  "a CredentialsStoreActor" should {

    when(cl.apply(Get(oidc.providers(0).jwkCert)))
      .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, jwk1))))
    when(cl.apply(Get(oidc.providers(1).jwkCert)))
      .thenReturn(Future.successful(HttpResponse(StatusCodes.NotFound)))

    val store = CredentialsStore("name")

    "return the correct key when it is present" in {
        store.fetchKey(TokenId(provider1Issuer, "kid1")).futureValue shouldBe a[PublicKey]
    }

    "return KidOrIssuerNotFound when the key is not present" in {
      ScalaFutures.whenReady(store.fetchKey(TokenId(oidc.providers(1).issuer, "kid2")).failed) { e =>
        e shouldBe a[KidOrIssuerNotFound.type]
      }
    }

    "ask to refresh the keys and verify a new key is present" in {

      when(cl.apply(Get(oidc.providers(1).jwkCert)))
        .thenReturn(Future.successful(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, jwk2))))

      store.refreshCredentials(oidc.providers(1))
      eventually {
        store.fetchKey(TokenId(oidc.providers(1).issuer, "kid2")).futureValue shouldBe a[PublicKey]
      }

    }
  }
}
