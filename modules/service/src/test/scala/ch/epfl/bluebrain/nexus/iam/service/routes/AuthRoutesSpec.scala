package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.UntypedHttpClient
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Future

class AuthRoutesSpec  extends WordSpecLike with Matchers with ScalatestRouteTest {
  private val oidcConfig = new OidcConfig(
      "http://example.com/authorize,",
      "http://example.com/token,",
      "http://example.com/userinfo,",
  )
  private val cl = new UntypedHttpClient[Future] {
    override def apply(req: HttpRequest) = Future.successful(HttpResponse(StatusCodes.OK))

    override def discardBytes(entity: HttpEntity) = ???

    override def toString(entity: HttpEntity) = ???
  }
  private val routes  = AuthRoutes(oidcConfig)(cl).routes

  "The IAM service" should {
    "forward requests to authorize endpoint" in {
      Get("/oauth2/authorize") ~> routes ~> check {
        fail()
      }
    }

    "forward requests to token endpoint" in {
      Get("/oauth2/token") ~> routes ~> check {
        fail()
      }
    }

    "forward requests to userinfo endpoint" in {
      Get("/oauth2/userinfo") ~> routes ~> check {
        fail()
      }
    }
  }

}
