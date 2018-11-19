package ch.epfl.bluebrain.nexus.iam.acls

import java.time.{Clock, Instant, ZoneId}
import java.util.regex.Pattern.quote

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.testkit.ScalatestRouteTest
import ch.epfl.bluebrain.nexus.commons.test
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.config.Settings
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.marshallers.instances._
import ch.epfl.bluebrain.nexus.iam.realms.Realms
import ch.epfl.bluebrain.nexus.iam.routes.AclsRoutes
import ch.epfl.bluebrain.nexus.iam.types.Identity._
import ch.epfl.bluebrain.nexus.iam.types._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.node.unsafe._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._
import com.typesafe.config.ConfigFactory
import io.circe.Json
import monix.eval.Task
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._

class AclsRoutesSpec
    extends WordSpecLike
    with Matchers
    with EitherValues
    with OptionValues
    with MockitoSugar
    with BeforeAndAfter
    with ScalatestRouteTest
    with test.Resources
    with Randomness
    with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig = PatienceConfig(3 second, 100 milliseconds)

  private implicit val appConfig = new Settings(ConfigFactory.parseResources("app.conf").resolve()).appConfig
    .copy(http = HttpConfig("some", 8080, "v1", "http://nexus.example.com"))
  private implicit val clock: Clock = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())

  private val acls: Acls[Task]     = mock[Acls[Task]]
  private val realms: Realms[Task] = mock[Realms[Task]]
  val routes                       = new AclsRoutes(acls, realms).routes

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

    implicit val caller = Caller(user, Set(user, group))

    val id   = url"https://bluebrain.github.io/nexus/acls/myorg/myproj".value
    val path = "myorg" / "myproj"

    val resourceAcl1 = ResourceF(base + "id1",
                                 1L,
                                 Set[AbsoluteIri](nxv.AccessControlList),
                                 clock.instant(),
                                 user,
                                 clock.instant(),
                                 user2,
                                 AccessControlList(user -> readWrite, group -> manage))
    val resourceAcl2 = ResourceF(base + "id2",
                                 2L,
                                 Set[AbsoluteIri](nxv.AccessControlList),
                                 clock.instant(),
                                 user,
                                 clock.instant(),
                                 user,
                                 AccessControlList(user -> readWrite))

    val aclsFetch = AccessControlLists(Path("one/two") -> resourceAcl1, Path("one") -> resourceAcl2)

    def response(rev: Long, createdBy: Identity, updatedBty: Identity, path: Path): Json =
      jsonContentOf(
        "/resources/write-response-routes.json",
        Map(quote("{path}")      -> path.repr.drop(1),
            quote("{createdBy}") -> createdBy.id.asString,
            quote("{updatedBy}") -> updatedBty.id.asString)
      ) deepMerge Json.obj("_rev" -> Json.fromLong(rev))

    when(realms.caller(AuthToken(token.token))).thenReturn(Task(Option(caller)))

    val responseMeta =
      ResourceMetadata(id, 1L, Set(nxv.AccessControlList), clock.instant(), user, clock.instant(), user)

    "create ACL" in {
      when(acls.replace(path, 0L, acl)).thenReturn(Task.pure[AclMetaOrRejection](Right(responseMeta)))

      Put(s"/v1/acls/myorg/myproj", aclJson) ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.Created
      }
    }

    "append ACL" in {
      when(acls.append(path, 1L, acl)).thenReturn(Task.pure[AclMetaOrRejection](Right(responseMeta)))
      val patch = aclJson deepMerge Json.obj("@type" -> Json.fromString("Append"))
      Patch(s"/v1/acls/myorg/myproj?rev=1", patch) ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.OK
      }
    }

    "subtract ACL" in {
      when(acls.subtract(path, 1L, acl)).thenReturn(Task.pure[AclMetaOrRejection](Right(responseMeta)))
      val patch = aclJson deepMerge Json.obj("@type" -> Json.fromString("Subtract"))
      Patch(s"/v1/acls/myorg/myproj?rev=1", patch) ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.OK
      }
    }

    "delete ACL" in {
      when(acls.delete(path, 1L)).thenReturn(Task.pure[AclMetaOrRejection](Right(responseMeta)))
      Delete(s"/v1/acls/myorg/myproj?rev=1") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual response(1L, user, user, path)
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true" in {
      when(acls.fetch(path)).thenReturn(Task.pure(resourceAcl1))
      Get(s"/v1/acls/myorg/myproj") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true and rev = 1" in {
      when(acls.fetch(path, 1L)).thenReturn(Task.pure(resourceAcl1))
      Get(s"/v1/acls/myorg/myproj?rev=1") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = false and rev = 2" in {
      when(acls.fetchUnsafe(path, 2L)).thenReturn(Task.pure(resourceAcl1))
      Get(s"/v1/acls/myorg/myproj?rev=2&self=false") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf("/acls/acls-routes.json")
        status shouldEqual StatusCodes.OK
      }
    }

    "get ACL self = true and ancestors = true" in {
      when(acls.list(path, self = true)).thenReturn(Task.pure(aclsFetch))
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

    "return error when invalid query parameter" in {
      Get(s"/v1/acls/myorg/myproj?rev=2ancestors=true") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf(
          "/acls/error.json",
          Map(quote("{code}") -> "IllegalParameter", quote("{msg}") -> "For input string: \\\\\"2ancestors=true\\\\\""))
        status shouldEqual StatusCodes.BadRequest
      }
    }

    "return error when making a call that returns exception on the ACLs" in {
      Get(s"/v1/acls/myorg/myProj2") ~> addCredentials(token) ~> routes ~> check {
        responseAs[Json] shouldEqual jsonContentOf(
          "/acls/error.json",
          Map(quote("{code}") -> "Unexpected", quote("{msg}") -> "Something went wrong. Please, try again later."))
        status shouldEqual StatusCodes.InternalServerError
      }
    }
  }
}
