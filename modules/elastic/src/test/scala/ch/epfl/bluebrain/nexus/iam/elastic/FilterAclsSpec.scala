package ch.epfl.bluebrain.nexus.iam.elastic

import java.time.Clock

import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.{ElasticClient, ElasticDecoder}
import ch.epfl.bluebrain.nexus.commons.es.server.embed.ElasticServer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{UntypedHttpClient, withAkkaUnmarshaller}
import ch.epfl.bluebrain.nexus.commons.test.{Randomness, Resources}
import ch.epfl.bluebrain.nexus.commons.types.Meta
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, AuthenticatedRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, QueryResults}
import ch.epfl.bluebrain.nexus.iam.core.{AuthenticatedUser, ServiceAccount}
import ch.epfl.bluebrain.nexus.iam.core.acls.Event.PermissionsAdded
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControl, AccessControlList, Permission, Permissions}
import ch.epfl.bluebrain.nexus.iam.elastic.query.FilterAcls
import ch.epfl.bluebrain.nexus.iam.elastic.types.{FullAccessControl, FullAccessControlList}
import ch.epfl.bluebrain.nexus.iam.elastic.SimpleIdentitySerialization._
import ch.epfl.bluebrain.nexus.service.http.Path._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import io.circe.java8.time._
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.Future
import scala.concurrent.duration._

class FilterAclsSpec
    extends ElasticServer
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with Randomness
    with Resources
    with Inspectors
    with BeforeAndAfterAll
    with Eventually
    with CancelAfterFailure
    with Assertions {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(10 seconds, 400 milliseconds)

  private implicit val cl: UntypedHttpClient[Future] = HttpClient.akkaHttpClient

  private implicit val D: Decoder[QueryResults[AclDocument]] = ElasticDecoder[AclDocument]
  private implicit val rsSearch: HttpClient[Future, QueryResults[AclDocument]] =
    withAkkaUnmarshaller[QueryResults[AclDocument]]
  private val client = ElasticClient[Future](esUri)

  private val settings = ElasticConfig(esUri, genString(length = 6), genString(length = 6))

  private val filter = FilterAcls(client, settings)
  private def getAll: Future[QueryResults[AclDocument]] =
    client.search[AclDocument](Json.obj("query" -> Json.obj("match_all" -> Json.obj())))(Pagination(0, 10000))

  "A FilterAcls" should {
    val indexer                = AclIndexer(client, settings)
    val userIdentity: Identity = UserRef("realm2", "user123")
    val authUser               = AuthenticatedRef(Some("one"))
    val meta                   = Meta(Anonymous(), Clock.systemUTC.instant())
    val laPerm                 = Permission("la")
    val pubTerm                = Permission("publish")
    val instPerm               = Permission("instantiate")
    val list = FullAccessControlList(
      (userIdentity, /, Permissions(Read)),
      (authUser, /, Permissions(Read)),
      (userIdentity, "first" / "second", Permissions(Write)),
      (authUser, "first" / "second", Permissions(Read)),
      (userIdentity, "firstother" / "second", Permissions(laPerm)),
      (authUser, "firstother" / "second", Permissions(Read, Write, Own)),
      (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
      (authUser, "first" / "second" / "third", Permissions(laPerm)),
      (authUser, "first" / "second" / "third" / "ab", Permissions(Own)),
      (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
      (authUser, "first" / "second" / "third" / "bc" / "cd", Permissions(instPerm)),
      (userIdentity, "first" / "third" / "bc" / "cd", Permissions(instPerm))
    )
    implicit val user = AuthenticatedUser(Set(userIdentity))

    "index several PermissionsAdded events" in {
      list.acl.groupBy(_.path).foreach {
        case (path, fullAcls) =>
          val acls = AccessControlList(fullAcls.map {
            case FullAccessControl(identity, _, perms) => AccessControl(identity, perms)
          }.toSet)
          indexer(PermissionsAdded(path, acls, meta)).futureValue
      }
      eventually {
        getAll.futureValue.total shouldEqual 15L
      }
    }

    "search for a user which is not indexed" in {
      filter("*" / "*", self = true, parents = true)(AuthenticatedUser(Set(UserRef("realm3", "someother")))).futureValue shouldEqual FullAccessControlList()
    }

    "return empty when called by a service account with self true" in {
      filter("first" / "second", self = true, parents = true)(ServiceAccount).futureValue shouldEqual FullAccessControlList()
    }

    "search for all users when called by a service account with self false" in {
      filter("first" / "second", self = false, parents = true)(ServiceAccount).futureValue shouldEqual FullAccessControlList(
        (authUser, /, Permissions(Read)),
        (userIdentity, /, Permissions(Read)),
        (authUser, "first" / "second", Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write))
      )
    }

    "search on path /*/*/*/*/*/*" in {
      filter("*" / "*" / "*" / "*" / "*" / "*", self = false, parents = false)(AuthenticatedUser(
        Set(UserRef("realm3", "someother"), userIdentity))).futureValue shouldEqual FullAccessControlList()

      filter("*" / "*" / "*" / "*" / "*" / "*", self = true, parents = false).futureValue shouldEqual FullAccessControlList()

      filter("*" / "*" / "*" / "*" / "*" / "*", parents = true, self = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (userIdentity, "first" / "third" / "bc" / "cd", Permissions(instPerm)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )

      filter("*" / "*" / "*" / "*" / "*" / "*", parents = true, self = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm)),
        (authUser, "first" / "second" / "third" / "ab", Permissions(Own)),
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (authUser, "first" / "second" / "third" / "bc" / "cd", Permissions(instPerm)),
        (userIdentity, "first" / "third" / "bc" / "cd", Permissions(instPerm)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )
    }

    "search on path /*/*/*/*/*" in {
      filter("*" / "*" / "*" / "*" / "*", self = false, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (authUser, "first" / "second" / "third" / "bc" / "cd", Permissions(instPerm)))

      filter("*" / "*" / "*" / "*" / "*", self = true, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)))

      filter("*" / "*" / "*" / "*" / "*", parents = true, self = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (userIdentity, "first" / "third" / "bc" / "cd", Permissions(instPerm)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )

      filter("*" / "*" / "*" / "*" / "*", parents = true, self = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm)),
        (authUser, "first" / "second" / "third" / "ab", Permissions(Own)),
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (authUser, "first" / "second" / "third" / "bc" / "cd", Permissions(instPerm)),
        (userIdentity, "first" / "third" / "bc" / "cd", Permissions(instPerm)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )
    }

    "search on path /*/*/*" in {
      filter("*" / "*" / "*", self = false, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm))
      )

      filter("*" / "*" / "*", self = true, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)))

      filter("*" / "*" / "*", parents = true, self = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )

      filter("*" / "*" / "*", parents = true, self = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )
    }

    "search on path first/*/*" in {

      filter("first" / "*" / "*", self = false, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm))
      )

      filter("first" / "*" / "*", self = true, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own))
      )

      filter("first" / "*" / "*", parents = true, self = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own))
      )

      filter("first" / "*" / "*", parents = true, self = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm))
      )
    }

    "search on path /first/second/third/ab" in {
      filter("first" / "second" / "third" / "ab", self = false, parents = false).futureValue shouldEqual FullAccessControlList(
        (authUser, "first" / "second" / "third" / "ab", Permissions(Own))
      )

      filter("first" / "second" / "third" / "ab", self = true, parents = false).futureValue shouldEqual FullAccessControlList()

      filter("first" / "second" / "third" / "ab", parents = true, self = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own))
      )

      filter("first" / "second" / "third" / "ab", parents = true, self = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm)),
        (authUser, "first" / "second" / "third" / "ab", Permissions(Own))
      )

    }

    "search on path /first/second/third/bc/*" in {
      filter("first" / "second" / "third" / "bc" / "*", self = false, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (authUser, "first" / "second" / "third" / "bc" / "cd", Permissions(instPerm)))

      filter("first" / "second" / "third" / "bc" / "*", self = true, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)))

      filter("first" / "second" / "third" / "bc" / "*", parents = true, self = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm))
      )

      filter("first" / "second" / "third" / "bc" / "*", parents = true, self = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm)),
        (userIdentity, "first" / "second" / "third" / "bc" / "cd", Permissions(pubTerm)),
        (authUser, "first" / "second" / "third" / "bc" / "cd", Permissions(instPerm))
      )
    }

    "search further where there are no permissions set" in {
      filter("first" / "second" / "third" / "bc" / "cd" / "e" / "*", parents = false, self = true).futureValue shouldEqual FullAccessControlList()
      filter("first" / "second" / "third" / "bc" / "cd" / "e" / "*", self = false, parents = false).futureValue shouldEqual FullAccessControlList()

    }

    "search on path /*/second/third" in {

      filter("*" / "second" / "third", self = false, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm))
      )
      filter("*" / "second" / "third", self = true, parents = false).futureValue shouldEqual FullAccessControlList(
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)))

      filter("*" / "second" / "third", self = true, parents = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )

      filter("*" / "second" / "third", self = false, parents = true).futureValue shouldEqual FullAccessControlList(
        (userIdentity, /, Permissions(Read)),
        (userIdentity, "first" / "second", Permissions(Write)),
        (userIdentity, "first" / "second" / "third", Permissions(Write, Own)),
        (authUser, "first" / "second" / "third", Permissions(laPerm)),
        (userIdentity, "firstother" / "second", Permissions(laPerm))
      )
    }
  }
}
