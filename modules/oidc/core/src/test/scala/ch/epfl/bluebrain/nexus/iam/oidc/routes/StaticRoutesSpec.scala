package ch.epfl.bluebrain.nexus.iam.oidc.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.iam.oidc.config.BuildInfo
import ch.epfl.bluebrain.nexus.iam.oidc.routes.StaticRoutes.ServiceDescription
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.{Matchers, WordSpecLike}

class StaticRoutesSpec extends WordSpecLike with Matchers with ScalatestRouteTest {

  "The IAM service" should {
    "return the appropriate service description" in {
      Get(s"/") ~> StaticRoutes().routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[ServiceDescription] shouldEqual ServiceDescription("iam-oidc-core", BuildInfo.version, "local")
      }
    }
  }
}
