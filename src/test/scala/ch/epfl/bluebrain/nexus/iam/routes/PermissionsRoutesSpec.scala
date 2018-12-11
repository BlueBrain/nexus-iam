package ch.epfl.bluebrain.nexus.iam.routes

import java.time.Instant
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.auth.AccessToken
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.permissions._
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.testsyntax._
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.{Caller, Permission, ResourceF}
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import monix.eval.Task
import org.mockito.matchers.MacroBasedMatchers
import org.mockito.{IdiomaticMockito, Mockito}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.duration._

//noinspection TypeAnnotation
class PermissionsRoutesSpec
    extends WordSpecLike
    with Matchers
    with ScalatestRouteTest
    with BeforeAndAfter
    with MacroBasedMatchers
    with Resources
    with ScalaFutures
    with IdiomaticMockito {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(3 second, 100 milliseconds)

  override def testConfig: Config = ConfigFactory.load("test.conf")

  private val appConfig: AppConfig = Settings(system).appConfig
  private implicit val http        = appConfig.http

  private val perms: Permissions[Task] = mock[Permissions[Task]]
  private val realms: Realms[Task]     = mock[Realms[Task]]

  before {
    Mockito.reset(perms, realms)
    realms.caller(any[AccessToken]) shouldReturn Task.pure(Caller.anonymous)
  }

  def response(rev: Long): Json =
    jsonContentOf(
      "/permissions/permissions-template.json",
      Map(quote("{createdBy}") -> Anonymous.id.asString, quote("{updatedBy}") -> Anonymous.id.asString)
    ) deepMerge Json.obj("_rev" -> Json.fromLong(rev))

  def resource(rev: Long, set: Set[Permission]): Resource =
    ResourceF(id, rev, types, deprecated = false, Instant.EPOCH, Anonymous, Instant.EPOCH, Anonymous, set)

  def metaResponse(rev: Long): Json =
    jsonContentOf(
      "/permissions/permissions-meta-template.json",
      Map(quote("{createdBy}") -> Anonymous.id.asString, quote("{updatedBy}") -> Anonymous.id.asString)
    ) deepMerge Json.obj("_rev" -> Json.fromLong(rev))

  def meta(rev: Long): ResourceF[Unit] =
    ResourceF.unit(id, rev, types, deprecated = false, Instant.EPOCH, Anonymous, Instant.EPOCH, Anonymous)

  def missingParams: Json =
    jsonContentOf("/permissions/missing-rev.json")

  "A PermissionsRoute" should {
    val routes = new PermissionsRoutes(perms, realms).routes
    "return the default minimum permissions" in {
      perms.fetch(any[Caller]) shouldReturn Task.pure(resource(0L, appConfig.permissions.minimum))
      Get("/v1/permissions") ~> routes ~> check {
        responseAs[Json].sort shouldEqual response(0L).sort
        status shouldEqual StatusCodes.OK
      }
    }
    "return missing rev params" when {
      "attempting to append" in {
        val json =
          Json.obj("@type" -> Json.fromString("Append"), "permissions" -> Json.arr(Json.fromString("random/a")))
        Patch("/v1/permissions", json) ~> routes ~> check {
          responseAs[Json].sort shouldEqual missingParams.sort
          status shouldEqual StatusCodes.BadRequest
        }
      }
      "attempting to subtract" in {
        val json =
          Json.obj("@type" -> Json.fromString("Subtract"), "permissions" -> Json.arr(Json.fromString("random/a")))
        Patch("/v1/permissions", json) ~> routes ~> check {
          responseAs[Json].sort shouldEqual missingParams.sort
          status shouldEqual StatusCodes.BadRequest
        }
      }
      "attempting to replace" in {
        val json = Json.obj("permissions" -> Json.arr(Json.fromString("random/a")))
        Put("/v1/permissions", json) ~> routes ~> check {
          responseAs[Json].sort shouldEqual missingParams.sort
          status shouldEqual StatusCodes.BadRequest
        }
      }
      "attempting to delete" in {
        Delete("/v1/permissions") ~> routes ~> check {
          responseAs[Json].sort shouldEqual missingParams.sort
          status shouldEqual StatusCodes.BadRequest
        }
      }
    }
    "replace permissions" in {
      perms.replace(any[Set[Permission]], 2L)(any[Caller]) shouldReturn Task.pure(Right(meta(0L)))
      val json = Json.obj("permissions" -> Json.arr(Json.fromString("random/a")))
      Put("/v1/permissions?rev=2", json) ~> routes ~> check {
        responseAs[Json].sort shouldEqual metaResponse(0L).sort
        status shouldEqual StatusCodes.OK
      }
    }
    "append new permissions" in {
      perms.append(any[Set[Permission]], 2L)(any[Caller]) shouldReturn Task.pure(Right(meta(0L)))
      val json = Json.obj("@type" -> Json.fromString("Append"), "permissions" -> Json.arr(Json.fromString("random/a")))
      Patch("/v1/permissions?rev=2", json) ~> routes ~> check {
        responseAs[Json].sort shouldEqual metaResponse(0L).sort
        status shouldEqual StatusCodes.OK
      }
    }
    "subtract permissions" in {
      perms.subtract(any[Set[Permission]], 2L)(any[Caller]) shouldReturn Task.pure(Right(meta(0L)))
      val json =
        Json.obj("@type" -> Json.fromString("Subtract"), "permissions" -> Json.arr(Json.fromString("random/a")))
      Patch("/v1/permissions?rev=2", json) ~> routes ~> check {
        responseAs[Json].sort shouldEqual metaResponse(0L).sort
        status shouldEqual StatusCodes.OK
      }
    }
    "delete permissions" in {
      perms.delete(2L)(any[Caller]) shouldReturn Task.pure(Right(meta(0L)))
      Delete("/v1/permissions?rev=2") ~> routes ~> check {
        responseAs[Json].sort shouldEqual metaResponse(0L).sort
        status shouldEqual StatusCodes.OK
      }
    }
  }

}
