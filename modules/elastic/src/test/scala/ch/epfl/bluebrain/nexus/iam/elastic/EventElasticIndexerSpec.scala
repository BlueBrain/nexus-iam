package ch.epfl.bluebrain.nexus.iam.elastic

import java.net.URLEncoder
import java.time.Clock

import akka.testkit.TestKit
import cats.instances.future._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticFailure.ElasticClientError
import ch.epfl.bluebrain.nexus.commons.es.client.{ElasticClient, ElasticDecoder, ElasticQueryClient}
import ch.epfl.bluebrain.nexus.commons.es.server.embed.ElasticServer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{UntypedHttpClient, withAkkaUnmarshaller}
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls.{AccessControlList, Meta, Permission, Permissions}
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.test.{Randomness, Resources}
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.ScoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, QueryResults}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._
import io.circe.{Decoder, Json}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.Future
import scala.concurrent.duration._

class EventElasticIndexerSpec
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
    PatienceConfig(9 seconds, 400 milliseconds)

  private implicit val config: Configuration                        = Configuration.default.withDiscriminator("type")
  private implicit val cl: UntypedHttpClient[Future]                = HttpClient.akkaHttpClient
  private implicit val D: Decoder[QueryResults[ElasticPermissions]] = ElasticDecoder[ElasticPermissions]
  private implicit val rsSearch: HttpClient[Future, QueryResults[ElasticPermissions]] =
    withAkkaUnmarshaller[QueryResults[ElasticPermissions]]
  private val client = ElasticClient[Future](esUri, ElasticQueryClient[Future](esUri))

  private val base     = s"http://127.0.0.1/v0"
  private val settings = ElasticIndexingSettings(base, genString(length = 6), genString(length = 6))

  private def getAll: Future[QueryResults[ElasticPermissions]] =
    client.search[ElasticPermissions](Json.obj("query" -> Json.obj("match_all" -> Json.obj())))(Pagination(0, 100))

  private def indexId(identity: Identity): String = URLEncoder.encode(s"${settings.indexPrefix}_${identity.id.show}", "UTF-8").toLowerCase

  private def genPath = genString(length = 4) / genString(length = 4) / genString(length = 4)

  "An EventElasticIndexer" should {
    val indexer                 = EventElasticIndexer(client, settings)
    val userIdentity: Identity  = UserRef("realm", "user1")
    val groupIdentity: Identity = GroupRef("realm", "group1")
    val anon                    = Anonymous()
    val meta                    = Meta(Anonymous(), Clock.systemUTC.instant())
    val path1                   = genPath
    val path2                   = genPath
    val path3                   = genPath ++ genPath

    "index a PermissionsAdded event on a path" in {
      val event   = PermissionsAdded(path1, AccessControlList(anon -> Permissions(Read)), meta)
      val index = indexId(anon)

      whenReady(client.existsIndex(index).failed) { e =>
        e shouldBe a[ElasticClientError]
      }
      indexer(event).futureValue
      eventually {
        val rs = getAll.futureValue
        rs.results.size shouldEqual 1
        rs.results.head.source shouldEqual ElasticPermissions(path1, anon, Permissions(Read))
        client.existsIndex(index).futureValue shouldEqual (())
      }
    }

    "index another PermissionsAdded event on the same path" in {
      val event = PermissionsAdded(path1,
                                   AccessControlList(anon         -> Permissions(Read, Write, Own),
                                                     userIdentity -> Permissions(Read, Write)),
                                   meta)
      val index = indexId(userIdentity)

      whenReady(client.existsIndex(index).failed) { e =>
        e shouldBe a[ElasticClientError]
      }
      indexer(event).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path1, anon, Permissions(Read, Write, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path1, userIdentity, Permissions(Read, Write)))
        )
        client.existsIndex(index).futureValue shouldEqual (())
      }
    }

    "index another PermissionsAdded event on a separate path" in {
      val event  = PermissionsAdded(path2, AccessControlList(userIdentity  -> Permissions(Read, Write, Own)), meta)
      val event2 = PermissionsAdded(path3, AccessControlList(groupIdentity -> Permissions(Read, Own)), meta)
      val event3 = PermissionsAdded(path3, AccessControlList(anon          -> Permissions(Own, Write)), meta)

      indexer(event).futureValue
      indexer(event2).futureValue
      indexer(event3).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path1, anon, Permissions(Read, Write, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path1, userIdentity, Permissions(Read, Write))),
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Read, Write, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions(Read, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, anon, Permissions(Own, Write)))
        )
      }
    }

    "index a PermissionsRemoved" in {
      val event = PermissionsRemoved(path3, anon, meta)
      indexer(event).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path1, anon, Permissions(Read, Write, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path1, userIdentity, Permissions(Read, Write))),
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Read, Write, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions(Read, Own)))
        )
      }
    }

    "index a PermissionsCleared" in {
      val event = PermissionsCleared(path1, meta)
      indexer(event).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Read, Write, Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions(Read, Own)))
        )
      }
    }

    "index a PermissionsSubtracted" in {
      val event = PermissionsSubtracted(path2, userIdentity, Permissions(Read, Write, Permission("publish")), meta)
      indexer(event).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions(Read, Own)))
        )
      }
    }

    "index another PermissionsSubtracted" in {
      indexer(PermissionsSubtracted(path2, userIdentity, Permissions(Write), meta)).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions(Read, Own)))
        )
      }

      indexer(PermissionsSubtracted(path3, groupIdentity, Permissions(Read, Own), meta)).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions.empty))
        )
      }
    }

    "index another PermissionsAdded on an empty permissions list" in {
      val event = PermissionsAdded(path3, AccessControlList(groupIdentity-> Permissions(Read, Write, Own)), meta)
      indexer(event).futureValue
      eventually {
        getAll.futureValue.results should contain theSameElementsAs List(
          ScoredQueryResult(1F, ElasticPermissions(path2, userIdentity, Permissions(Own))),
          ScoredQueryResult(1F, ElasticPermissions(path3, groupIdentity, Permissions(Read, Write, Own)))
        )
      }
    }
  }
}
