package ch.epfl.bluebrain.nexus.iam.routes

import java.time.Instant
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.Directives._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.iam.auth.AccessToken
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.realms.{types, ActiveRealm, Realms, Resource, ResourceMetadata}
import ch.epfl.bluebrain.nexus.iam.testsyntax._
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.{Caller, GrantType, Label, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.Url
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.Json
import monix.eval.Task
import org.mockito.matchers.MacroBasedMatchers
import org.mockito.{IdiomaticMockito, Mockito}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.duration._

//noinspection TypeAnnotation,NameBooleanParameters
class RealmsRoutesSpec
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

  private val realms: Realms[Task] = mock[Realms[Task]]

  before {
    Mockito.reset(realms)
    realms.caller(any[AccessToken]) shouldReturn Task.pure(Caller.anonymous)
  }

  def response(label: Label, rev: Long, deprecated: Boolean): Json =
    jsonContentOf(
      "/realms/realm-template.json",
      Map(
        quote("{label}")      -> label.value,
        quote("{createdBy}")  -> Anonymous.id.asString,
        quote("{updatedBy}")  -> Anonymous.id.asString,
        quote("{deprecated}") -> deprecated.toString,
      )
    ) deepMerge Json.obj("_rev" -> Json.fromLong(rev))

  def listResponse(label: Label, deprecated: Boolean): Json =
    jsonContentOf(
      "/realms/list-realms-template.json",
      Map(
        quote("{label}")      -> label.value,
        quote("{createdBy}")  -> Anonymous.id.asString,
        quote("{updatedBy}")  -> Anonymous.id.asString,
        quote("{deprecated}") -> deprecated.toString,
      )
    )

  def resource(label: Label, rev: Long, realm: ActiveRealm): Resource =
    ResourceF(label.toIri(http.realmsIri), rev, types, Instant.EPOCH, Anonymous, Instant.EPOCH, Anonymous, Right(realm))

  def metaResponse(label: Label, rev: Long, deprecated: Boolean): Json =
    jsonContentOf(
      "/realms/realm-meta-template.json",
      Map(
        quote("{label}")      -> label.value,
        quote("{createdBy}")  -> Anonymous.id.asString,
        quote("{updatedBy}")  -> Anonymous.id.asString,
        quote("{deprecated}") -> deprecated.toString,
      )
    ) deepMerge Json.obj("_rev" -> Json.fromLong(rev))

  def meta(label: Label, rev: Long, deprecated: Boolean): ResourceMetadata =
    ResourceF(
      label.toIri(http.realmsIri),
      rev,
      types,
      Instant.EPOCH,
      Anonymous,
      Instant.EPOCH,
      Anonymous,
      label -> deprecated
    )

  "A RealmsRoute" should {
    val routes       = handleRejections(RejectionHandling.notFound())(new RealmsRoutes(realms).routes)
    val label        = Label.unsafe("therealm")
    val name         = "The Realm"
    val openIdConfig = Url("http://localhost:8080/realm").right.get
    val logo         = Url("http://localhost:8080/realm/logo").right.get
    "create a new realm" in {
      realms.create(any[Label], any[String], any[Url], any[Option[Url]])(any[Caller]) shouldReturn Task.pure(
        Right(meta(label, 1L, false)))
      Put("/v1/realms/therealm", jsonContentOf("/realms/create-realm.json")) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Json].sort shouldEqual metaResponse(label, 1L, false).sort
      }
    }
    "update an existing realm" in {
      realms.update(any[Label], any[Long], any[Option[String]], any[Option[Url]], any[Option[Url]])(any[Caller]) shouldReturn Task
        .pure(Right(meta(label, 1L, false)))
      Put("/v1/realms/therealm?rev=1", jsonContentOf("/realms/create-realm.json")) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].sort shouldEqual metaResponse(label, 1L, false).sort
      }
    }
    val realm = ActiveRealm(label, name, openIdConfig, "issuer", Set(GrantType.Implicit), Some(logo), Set.empty)
    "fetch a realm by id" in {
      realms.fetch(any[Label])(any[Caller]) shouldReturn Task.pure(Some(resource(label, 1L, realm)))
      Get("/v1/realms/therealm") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].sort shouldEqual response(label, 1L, false).sort
      }
    }
    "fetch a realm by id and rev" in {
      realms.fetch(any[Label], any[Long])(any[Caller]) shouldReturn Task.pure(Some(resource(label, 1L, realm)))
      Get("/v1/realms/therealm?rev=5") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].sort shouldEqual response(label, 1L, false).sort
      }
    }
    "list realms" in {
      realms.list(any[Caller]) shouldReturn Task.pure(List(resource(label, 1L, realm)))
      Get("/v1/realms") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].sort shouldEqual listResponse(label, false).sort
      }
    }
    "deprecate a realm" in {
      realms.deprecate(any[Label], any[Long])(any[Caller]) shouldReturn Task.pure(Right(meta(label, 1L, true)))
      Delete("/v1/realms/therealm?rev=5") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Json].sort shouldEqual metaResponse(label, 1L, true).sort
      }
    }
    "access an endpoint that does not exists" in {
      Get("/v1/other/therealm?rev=5") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Json] shouldEqual jsonContentOf("/resources/not-found.json")
      }
    }
  }

}
