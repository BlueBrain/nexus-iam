package ch.epfl.bluebrain.nexus.iam.service.routes

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.TimeUnit

import akka.cluster.Cluster
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestDuration
import akka.util.Timeout
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.http.UnexpectedUnsuccessfulHttpResponse
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.State.Initial
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.auth.AuthenticatedUser
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._
import ch.epfl.bluebrain.nexus.iam.service.auth.AuthenticationFailure._
import ch.epfl.bluebrain.nexus.iam.service.auth.DownstreamAuthClient
import ch.epfl.bluebrain.nexus.iam.service.config.Settings
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejections._
import ch.epfl.bluebrain.nexus.iam.service.routes.Error.classNameOf
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.mockito.Mockito.when
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Random

class AclsRoutesSpec extends AclsRoutesSpecInstances {

  "The ACL service" should {

    "reject unauthorized requests" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(Anonymous -> ownReadWrite, alice -> readWrite)) ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Delete(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Get(s"/acls${path.repr}?all=true") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
      Get(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

    "reject command with invalid payload" in {
      val path    = Path(s"/some/$rand")
      val content = HttpEntity(ContentTypes.`application/json`, """{"foo": ["bar"]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[WrongOrInvalidJson.type]
      }
    }

    "reject command with invalid permissions" in {
      val path = Path(s"/some/$rand")
      val content =
        HttpEntity(ContentTypes.`application/json`, """{"acl":[{"permissions": ["random123"], "identity": {}}]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalPermissionString.type]
      }
    }

    "reject command with invalid identity" in {
      val path = Path(s"/some/$rand")
      val content = HttpEntity(ContentTypes.`application/json`,
                               """{"acl":[{"permissions": ["read"], "identity": {"origin": "foö://bar"}}]}""")
      Put(s"/acls${path.repr}", content) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[IllegalIdentityFormat.type]
      }
    }

    "reject requesting invalid HTTP verb" in {
      val path = Path(s"/some/$rand")
      Options(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].code shouldEqual classNameOf[MethodNotSupported.type]
      }
    }

    "reject clearing nonexistent permissions" in {
      val path = Path(s"/some/$rand")
      Delete(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[CannotClearNonexistentPermissions.type]
      }
    }

    "reject creating empty permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(Anonymous -> Permissions.empty)) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotCreateVoidPermissions.type]
      }
    }

    "reject adding empty permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(alice -> ownReadWrite)) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Post(s"/acls${path.repr}", AccessControl(Anonymous, Permissions.empty)) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotAddVoidPermissions.type]
      }
    }

    "clear permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(Anonymous -> ownReadWrite, alice -> readWrite)) ~> addCredentials(
        credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Delete(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
      Get(s"/acls${path.repr}?all=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList].acl shouldBe empty
      }
      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(alice -> own)
      }
    }

    "create and get permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}",
          AccessControlList(someGroup  -> ownReadWrite,
                            otherGroup -> ownReadWrite,
                            alice      -> readWrite,
                            Anonymous  -> read)) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Get(s"/acls${path.repr}?all=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(
          someGroup  -> ownReadWrite,
          otherGroup -> ownReadWrite,
          alice      -> readWrite,
          Anonymous  -> read
        )
      }
      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(
          someGroup -> ownReadWrite,
          alice     -> ownReadWrite,
          Anonymous -> read
        )
      }
    }

    "add permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(alice -> readWrite)) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Get(s"/acls${path.repr}") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(alice -> ownReadWrite)
      }
      Post(s"/acls${path.repr}", AccessControl(Anonymous, readWrite)) ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(Anonymous, readWrite)
      }
      Get(s"/acls${path.repr}?all=true") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList] shouldEqual AccessControlList(Anonymous -> readWrite, alice -> readWrite)
      }
    }

    "handle downstream error codes" in {
      when(dsac.getUser(credentials)).thenReturn(Future.failed(UnauthorizedCaller))
      Get("/acls/a/b/c") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.Unauthorized
      }

      when(dsac.getUser(credentials)).thenReturn(Future.failed(
        UnexpectedAuthenticationFailure(UnexpectedUnsuccessfulHttpResponse(HttpResponse(StatusCodes.BadGateway)))))
      Get("/acls/a/b/c") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.BadGateway
      }

      when(dsac.getUser(credentials)).thenReturn(Future.failed(new Exception))
      Get("/acls/a/b/c") ~> addCredentials(credentials) ~> routes ~> check {
        status shouldEqual StatusCodes.InternalServerError
      }

      when(dsac.getUser(credentials)).thenReturn(Future.successful(user)) // reset
    }

  }
}

abstract class AclsRoutesSpecInstances
    extends WordSpecLike
    with Matchers
    with Eventually
    with ScalatestRouteTest
    with BeforeAndAfterAll
    with MockitoSugar {
  private val appConfig                        = Settings(system).appConfig
  private val cl                               = Clock.fixed(Instant.ofEpochMilli(1), ZoneId.systemDefault())
  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = 5 seconds, interval = 50 millis)
  implicit val tm: Timeout                     = Timeout(appConfig.runtime.defaultTimeout.toMillis, TimeUnit.MILLISECONDS)
  implicit val rt: RouteTestTimeout            = RouteTestTimeout(5.seconds.dilated)

  protected val ownReadWrite = Permissions(Own, Read, Write)
  protected val readWrite    = Permissions(Read, Write)
  protected val own          = Permissions(Own)
  protected val read         = Permissions(Read)
  protected val local        = Uri("http://localhost/realm")
  protected val alice        = UserRef(local, "alice")
  protected val someGroup    = GroupRef(local, "some-group")
  protected val otherGroup   = GroupRef(local, "other-group")
  protected val dsac         = mock[DownstreamAuthClient[Future]]
  protected val credentials  = OAuth2BearerToken("token")
  protected val user         = AuthenticatedUser(Set(Anonymous, AuthenticatedRef(local), someGroup, alice))

  var routes: Route = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    when(dsac.getUser(credentials)).thenReturn(Future.successful(user))
    val p       = Promise[Unit]()
    val cluster = Cluster(system)
    cluster.registerOnMemberUp {
      val aggregate =
        ShardingAggregate("permission", SourcingAkkaSettings(journalPluginId = "inmemory-read-journal"))(Initial,
                                                                                                         Acls.next,
                                                                                                         Acls.eval)
      val acl = Acls[Future](aggregate, cl)
      acl.create(Path./, AccessControlList(alice -> own))(alice)
      routes = new AclsRoutes(acl, dsac).routes
      p.success(())
    }
    cluster.join(cluster.selfAddress)
    Await.result(p.future, rt.duration)
  }

  def rand: String = Math.abs(Random.nextLong).toString

}
