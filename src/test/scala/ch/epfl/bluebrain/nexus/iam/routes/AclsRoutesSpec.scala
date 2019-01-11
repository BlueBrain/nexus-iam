package ch.epfl.bluebrain.nexus.iam.routes

import java.time.{Clock, Instant, ZoneId}
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.test
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.ExpectedException
import ch.epfl.bluebrain.nexus.iam.acls._
import ch.epfl.bluebrain.nexus.iam.auth.AccessToken
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.types.Identity._
import ch.epfl.bluebrain.nexus.iam.types._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.Iri.{AbsoluteIri, Path}
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import com.typesafe.config.ConfigFactory
import io.circe.Json
import monix.eval.Task
import org.mockito.Mockito
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._

//noinspection NameBooleanParameters
class AclsRoutesSpec
    extends WordSpecLike
    with Matchers
    with EitherValues
    with OptionValues
    with IdiomaticMockitoFixture
    with BeforeAndAfter
    with ScalatestRouteTest
    with test.Resources
    with Randomness
    with ScalaFutures {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(3 second, 100 milliseconds)

  private val http = HttpConfig("some", 8080, "v1", "http://nexus.example.com")
  private implicit val appConfig: AppConfig = new Settings(ConfigFactory.parseResources("app.conf").resolve()).appConfig
    .copy(http = http)
  private implicit val clock: Clock = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())

  private val acls: Acls[Task]     = mock[Acls[Task]]
  private val realms: Realms[Task] = mock[Realms[Task]]
  private val routes               = new AclsRoutes(acls, realms).routes

  before {
    Mockito.reset(acls)
  }

  "ACL routes" should {
    val user      = User("uuid", "realm")
    val user2     = User("uuid2", "realm")
    val group     = Group("mygroup", "myrealm")
    val readWrite = Set(Permission("acls/read").value, Permission("acls/write").value)
    val manage    = Set(Permission("acls/manage").value)

    val aclJson = jsonContentOf("/acls/acl.json")
    val acl     = AccessControlList(user -> readWrite, group -> manage)
    val token   = OAuth2BearerToken("valid")

    implicit val caller: Caller = Caller(user, Set(user, group))

    val id   = url"https://bluebrain.github.io/nexus/acls/myorg/myproj".value
    val path = "myorg" / "myproj"

    val resourceAcl1 = ResourceF(
      http.aclsIri + "id1",
      1L,
      Set[AbsoluteIri](nxv.AccessControlList),
      clock.instant(),
      user,
      clock.instant(),
      user2,
      AccessControlList(user -> readWrite, group -> manage)
    )
    val resourceAcl2 = ResourceF(http.aclsIri + "id2",
                                 2L,
                                 Set[AbsoluteIri](nxv.AccessControlList),
                                 clock.instant(),
                                 user,
                                 clock.instant(),
                                 user,
                                 AccessControlList(user -> readWrite))

    val aclsFetch =
      AccessControlLists(Path("/one/two").right.value -> resourceAcl1, Path("/one").right.value -> resourceAcl2)

    def response(rev: Long, createdBy: Identity, updatedBty: Identity, path: Path): Json =
      jsonContentOf(
        "/resources/write-response-routes.json",
        Map(quote("{path}")      -> path.asString.drop(1),
            quote("{createdBy}") -> createdBy.id.asString,
            quote("{updatedBy}") -> updatedBty.id.asString)
      ) deepMerge Json.obj("_rev" -> Json.fromLong(rev))

    realms.caller(AccessToken(token.token)) shouldReturn Task.pure(caller)

    val responseMeta =
      ResourceMetadata(id, 1L, Set(nxv.AccessControlList), clock.instant(), user, clock.instant(), user)

    "create ACL" in {
      acls.replace(path, 0L, acl) shouldReturn Task.pure[MetaOrRejection](Right(responseMeta))

      Put(s"/v1/acls/myorg/myproj", aclJson) ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.Created
      }
    }

    "append ACL" in {
      acls.append(path, 1L, acl) shouldReturn Task.pure[MetaOrRejection](Right(responseMeta))
      val patch = aclJson deepMerge Json.obj("@type" -> Json.fromString("Append"))
      Patch(s"/v1/acls/myorg/myproj?rev=1", patch) ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.OK
      }
    }

    "subtract ACL" in {
      acls.subtract(path, 1L, acl) shouldReturn Task.pure[MetaOrRejection](Right(responseMeta))
      val patch = aclJson deepMerge Json.obj("@type" -> Json.fromString("Subtract"))
      Patch(s"/v1/acls/myorg/myproj?rev=1", patch) ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.OK
      }
    }

    "delete ACL" in {
      acls.delete(path, 1L) shouldReturn Task.pure[MetaOrRejection](Right(responseMeta))
      Delete(s"/v1/acls/myorg/myproj?rev=1") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true" in {
      acls.fetch(path, self = true) shouldReturn Task.pure(Option(resourceAcl1))
      Get(s"/v1/acls/myorg/myproj") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true and rev = 1" in {
      acls.fetch(path, 1L, self = true) shouldReturn Task.pure(Option(resourceAcl1))
      Get(s"/v1/acls/myorg/myproj?rev=1") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true with path containing *" in {
      acls.list(Path("/myorg/*").right.value, ancestors = false, self = true) shouldReturn Task.pure(aclsFetch)
      Get(s"/v1/acls/myorg/*") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = false and rev = 2 when response is an empty ACL" in {
      acls.fetch(path, 2L, self = false) shouldReturn Task.pure(Option(resourceAcl1.map(_ => AccessControlList.empty)))
      Get(s"/v1/acls/myorg/myproj?rev=2&self=false") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes-empty.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = false and rev = 2 when response is None" in {
      acls.fetch(path, 2L, self = false) shouldReturn Task.pure[ResourceOpt](None)
      Get(s"/v1/acls/myorg/myproj?rev=2&self=false") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes-empty.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true and ancestors = true" in {
      acls.list(path, ancestors = true, self = true) shouldReturn Task.pure(aclsFetch)
      Get(s"/v1/acls/myorg/myproj?ancestors=true&self=true") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "return error when getting ACL with rev and ancestors = true" in {
      Get(s"/v1/acls/myorg/myproj?rev=2&ancestors=true") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf(
          "/acls/error.json",
          Map(quote("{code}") -> "IllegalParameter",
              quote("{msg}")  -> "'rev' and 'ancestors' query parameters cannot be present simultaneously.")
        )
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return error when getting ACL with rev and path containing *" in {
      Get(s"/v1/acls/myorg/*?rev=2") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf(
          "/acls/error.json",
          Map(quote("{code}") -> "IllegalParameter",
              quote("{msg}")  -> "'rev' query parameter and path containing '*' cannot be present simultaneously.")
        )
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return error when invalid query parameter" in {
      Get(s"/v1/acls/myorg/myproj?rev=2ancestors=true") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf(
          "/acls/error.json",
          Map(quote("{code}") -> "IllegalParameter", quote("{msg}") -> "For input string: \\\\\"2ancestors=true\\\\\""))
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return error when making a call that returns exception on the ACLs" in {
      acls.fetch(path, self = true) shouldReturn Task.raiseError(ExpectedException)
      Get(s"/v1/acls/myorg/myproj") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf(
          "/acls/error.json",
          Map(quote("{code}") -> "Unexpected",
              quote("{msg}")  -> "The system experienced an unexpected error, please try again later."))
        status shouldEqual StatusCodes.InternalServerError
      }
    }

    "return error when path contains double slash" in {
      Get(s"/v1/acls/myorg//") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/error.json",
                                                   Map(quote("{code}") -> "IllegalParameter",
                                                       quote("{msg}")  -> "path '/myorg//' cannot contain double slash"))
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
}
