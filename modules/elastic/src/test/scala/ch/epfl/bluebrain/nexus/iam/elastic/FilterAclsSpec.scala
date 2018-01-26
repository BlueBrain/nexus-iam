package ch.epfl.bluebrain.nexus.iam.elastic

import java.time.Clock

import akka.testkit.TestKit
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.es.client.{ElasticClient, ElasticDecoder, ElasticQueryClient}
import ch.epfl.bluebrain.nexus.commons.es.server.embed.ElasticServer
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient.{UntypedHttpClient, withAkkaUnmarshaller}
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Permission._
import ch.epfl.bluebrain.nexus.commons.iam.acls._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.{Anonymous, AuthenticatedRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.SimpleIdentitySerialization._
import ch.epfl.bluebrain.nexus.commons.test.{Randomness, Resources}
import ch.epfl.bluebrain.nexus.commons.types.search.{Pagination, QueryResults}
import ch.epfl.bluebrain.nexus.iam.elastic.query.FilterAcls
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.circe.{Decoder, Json}
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
  private val client = ElasticClient[Future](esUri, ElasticQueryClient[Future](esUri))

  private val base     = s"http://127.0.0.1/v0"
  private val settings = ElasticConfig(base, genString(length = 6), genString(length = 6))

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
    val list: List[(Path, AccessControlList)] = List(
      /                  -> AccessControlList(userIdentity -> Permissions(Read), authUser  -> Permissions(Read)),
      "first" / "second" -> AccessControlList(userIdentity -> Permissions(Write), authUser -> Permissions(Read)),
      "firstother" / "second" -> AccessControlList(authUser -> Permissions(Read, Write, Own),
                                                   userIdentity -> Permissions(laPerm)),
      "first" / "second" / "third" -> AccessControlList(userIdentity -> Permissions(Write, Own),
                                                        authUser -> Permissions(laPerm)),
      "first" / "second" / "third" / "ab" -> AccessControlList(authUser -> Permissions(Own)),
      "first" / "second" / "third" / "bc" / "cd" -> AccessControlList(userIdentity -> Permissions(pubTerm),
                                                                      authUser -> Permissions(instPerm))
    )

    "index several PermissionsAdded events" in {
      forAll(list) {
        case (path, acls) =>
          indexer(PermissionsAdded(path, acls, meta)).futureValue
      }
      eventually {
        getAll.futureValue.total shouldEqual 11L
      }
    }

    "search for all permissions for a user" in {
      val result = filter(Set(userIdentity)).futureValue
      result shouldEqual IdentityAccessControlList(
        userIdentity ->
          List(
            PathAccessControl(/, Permissions(Read)),
            PathAccessControl("first" / "second", Permissions(Read, Write)),
            PathAccessControl("first" / "second" / "third", Permissions(Read, Write, Own)),
            PathAccessControl("first" / "second" / "third" / "bc" / "cd", Permissions(Read, Write, Own, pubTerm)),
            PathAccessControl("firstother" / "second", Permissions(Read, laPerm))
          ))
    }

    "search for all permissions for a user with a max. depth of 3" in {
      filter(Set(userIdentity), pathDepthOpt = Some(3)).futureValue shouldEqual IdentityAccessControlList(
        userIdentity ->
          List(
            PathAccessControl(/, Permissions(Read)),
            PathAccessControl("first" / "second", Permissions(Read, Write)),
            PathAccessControl("first" / "second" / "third", Permissions(Read, Write, Own)),
            PathAccessControl("firstother" / "second", Permissions(Read, laPerm))
          ))
    }

    "search for all permissions for a user with a max. depth of 3 with starting path" in {
      filter(Set(userIdentity), Path("first"), Some(3)).futureValue shouldEqual IdentityAccessControlList(
        userIdentity ->
          List(
            PathAccessControl("first" / "second", Permissions(Read, Write)),
            PathAccessControl("first" / "second" / "third", Permissions(Read, Write, Own))
          ))
    }

    "search for all permissions for a user without a max. depth and with a starting path" in {
      filter(Set(userIdentity), "first" / "second" / "third" / "bc").futureValue shouldEqual IdentityAccessControlList(
        userIdentity ->
          List(PathAccessControl("first" / "second" / "third" / "bc" / "cd", Permissions(Read, Write, Own, pubTerm))))
    }

    "search for all permissions on several identities" in {
      filter(Set(userIdentity, authUser), "first" / "second" / "third").futureValue shouldEqual IdentityAccessControlList(
        userIdentity ->
          List(
            PathAccessControl("first" / "second" / "third", Permissions(Read, Write, Own)),
            PathAccessControl("first" / "second" / "third" / "bc" / "cd", Permissions(Read, Write, Own, pubTerm))
          ),
        authUser -> List(
          PathAccessControl("first" / "second" / "third", Permissions(Read, laPerm)),
          PathAccessControl("first" / "second" / "third" / "ab", Permissions(Read, Own, laPerm)),
          PathAccessControl("first" / "second" / "third" / "bc" / "cd", Permissions(Read, laPerm, instPerm))
        )
      )
    }
  }
}
