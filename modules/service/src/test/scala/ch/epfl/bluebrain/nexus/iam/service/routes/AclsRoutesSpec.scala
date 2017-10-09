package ch.epfl.bluebrain.nexus.iam.service.routes

import java.time.{Clock, Instant, ZoneId}
import java.util.concurrent.TimeUnit

import akka.cluster.Cluster
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.util.Timeout
import akka.testkit.TestDuration
import cats.instances.future._
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls._
import ch.epfl.bluebrain.nexus.iam.core.acls.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.State.Initial
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._
import ch.epfl.bluebrain.nexus.iam.service.config.Settings
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejections._
import ch.epfl.bluebrain.nexus.iam.service.routes.Error.classNameOf
import ch.epfl.bluebrain.nexus.sourcing.akka.{ShardingAggregate, SourcingAkkaSettings}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import org.scalatest._
import org.scalatest.concurrent.Eventually

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.Random

class AclsRoutesSpec extends AclsRoutesSpecInstances {

  "The ACL service" should {

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
      Options(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.MethodNotAllowed
        responseAs[Error].code shouldEqual classNameOf[MethodNotSupported.type]
      }
    }

    "reject clearing nonexistent permissions" in {
      val path = Path(s"/some/$rand")
      Delete(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[CannotClearNonexistentPermissions.type]
      }
    }

    "reject creating empty permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(Anonymous -> Permissions.empty)) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotCreateVoidPermissions.type]
      }
    }

    "reject adding empty permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(alice -> ownReadWrite)) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Post(s"/acls${path.repr}", AccessControl(Anonymous, Permissions.empty)) ~> routes ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Error].code shouldEqual classNameOf[CannotAddVoidPermissions.type]
      }
    }

    "clear permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(Anonymous -> ownReadWrite, alice -> readWrite)) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Delete(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
      Get(s"/acls${path.repr}?all=true") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList].acl shouldBe empty
      }
      Get(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldBe AccessControl(Anonymous, Permissions.empty)
      }
    }

    "create and get permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(someGroup -> ownReadWrite, alice -> readWrite, Anonymous -> read)) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Get(s"/acls${path.repr}?all=true") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList].acl should contain theSameElementsAs Seq(
          AccessControl(Anonymous, read),
          AccessControl(alice, readWrite),
          AccessControl(someGroup, ownReadWrite)
        )
      }
      Get(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(Anonymous, read)
      }
    }

    "add permissions" in {
      val path = Path(s"/some/$rand")
      Put(s"/acls${path.repr}", AccessControlList(Anonymous -> ownReadWrite)) ~> routes ~> check {
        status shouldEqual StatusCodes.Created
      }
      Get(s"/acls${path.repr}") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(Anonymous, ownReadWrite)
      }
      Post(s"/acls${path.repr}", AccessControl(alice, readWrite)) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControl] shouldEqual AccessControl(alice, readWrite)
      }
      Get(s"/acls${path.repr}?all=true") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[AccessControlList].acl should contain theSameElementsAs Seq(AccessControl(Anonymous, ownReadWrite),
                                                                               AccessControl(alice, readWrite))
      }
    }

  }
}

abstract class AclsRoutesSpecInstances
    extends WordSpecLike
    with Matchers
    with Eventually
    with ScalatestRouteTest
    with BeforeAndAfterAll {
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

  var routes: Route = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val p       = Promise[Unit]()
    val cluster = Cluster(system)
    cluster.registerOnMemberUp {
      val aggregate =
        ShardingAggregate("permission", SourcingAkkaSettings(journalPluginId = "inmemory-read-journal"))(Initial,
                                                                                                         Acls.next,
                                                                                                         Acls.eval)
      val acl = Acls[Future](aggregate, cl)
      routes = Route.seal(new AclsRoutes(acl).routes)
      p.success(())
    }
    cluster.join(cluster.selfAddress)
    Await.result(p.future, rt.duration)
  }

  def rand: String = Math.abs(Random.nextLong).toString
}
