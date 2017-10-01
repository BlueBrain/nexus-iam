package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.iam.service.types.{Boxed, Link, ServiceDescription}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest.{Matchers, WordSpecLike}

class StaticRoutesSpec extends WordSpecLike with Matchers with ScalatestRouteTest {

  private val publicUri = Uri("http://localhost:8080")
  private val base      = ""
  private val name      = "iam"
  private val version   = "0.1.0"
  private val prefix    = "v0"
  private val routes    = StaticRoutes(name, version, publicUri, prefix).routes

  "The IAM service" should {
    "return the appropriate service description" in {
      Get(s"/$base") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val expected = Boxed(ServiceDescription(name, version), List(Link("api", s"$publicUri/$prefix/acls")))
        responseAs[Boxed[ServiceDescription]] shouldEqual expected
      }
    }
  }
}
