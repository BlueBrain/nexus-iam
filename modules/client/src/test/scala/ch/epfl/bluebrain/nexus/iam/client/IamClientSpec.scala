package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.Caller._
import ch.epfl.bluebrain.nexus.iam.client.IamClient.User
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.iam.client.types.Path._
import ch.epfl.bluebrain.nexus.iam.client.types.Permission._
import ch.epfl.bluebrain.nexus.iam.client.types._
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class IamClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter
    with MockitoSugar {

  private implicit val ec: ExecutionContext = system.dispatcher
  private implicit val iamUri: IamUri       = IamUri("http://localhost:8080")
  private val credentials                   = AuthToken("validToken")
  private val identities: Set[Identity] =
    Set(GroupRef("BBP", "group1"), GroupRef("BBP", "group2"), UserRef("realm", "f:someUUID:username"))
  private val identitiesWithFilteredGroups: Set[Identity] =
    Set(GroupRef("BBP", "group1"), UserRef("realm", "f:someUUID:username"))

  implicit val aclsClient: HttpClient[Future, FullAccessControlList] = mock[HttpClient[Future, FullAccessControlList]]
  implicit val userClient: HttpClient[Future, User]                  = mock[HttpClient[Future, User]]

  val client = IamClient.fromFuture

  before {
    Mockito.reset(aclsClient)
    Mockito.reset(userClient)
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(5 seconds, 200 milliseconds)

  "An IamClient" should {

    "return unathorized whenever the token is wrong" in {
      implicit val cred: Option[AuthToken] = Some(AuthToken("invalidToken"))
      val path                             = "oauth2" / "user"
      val request                          = requestFrom(path, Query("filterGroups" -> "false"))
      when(userClient(request)).thenReturn(Future.failed(UnauthorizedAccess))

      ScalaFutures.whenReady(client.getCaller().failed, Timeout(patienceConfig.timeout)) { e =>
        e shouldBe a[UnauthorizedAccess.type]
      }
    }

    "return anonymous caller whenever there is no token provided" in {
      implicit val cred: Option[AuthToken] = None
      client.getCaller().futureValue shouldEqual AnonymousCaller
    }

    "return an authenticated caller whenever the token provided is correct" in {
      implicit val cred: Option[AuthToken] = Some(credentials)
      val path                             = "oauth2" / "user"
      val request                          = requestFrom(path, Query("filterGroups" -> "false"))
      when(userClient(request)).thenReturn(Future.successful(User(identities)))
      client.getCaller().futureValue shouldEqual AuthenticatedCaller(credentials,
                                                                     UserRef("realm", "f:someUUID:username"),
                                                                     identities)
    }

    "return an authenticated caller with filtered groups" in {
      implicit val cred: Option[AuthToken] = Some(credentials)
      val path                             = "oauth2" / "user"
      val request                          = requestFrom(path, Query("filterGroups" -> "true"))
      when(userClient(request)).thenReturn(Future.successful(User(identitiesWithFilteredGroups)))
      client.getCaller(filterGroups = true).futureValue shouldEqual AuthenticatedCaller(credentials,
                                                                                        UserRef("realm",
                                                                                                "f:someUUID:username"),
                                                                                        identitiesWithFilteredGroups)
    }

    "return expected acls whenever the caller is authenticated" in {
      implicit val caller: Caller =
        AuthenticatedCaller(credentials, UserRef("realm", "f:someUUID:username"), identities)
      val expected = FullAccessControlList(
        (GroupRef("BBP", "group1"), Path("/acls/prefix/some/resource/one"), Permissions(Own, Read, Write)))
      val path = "acls" / "prefix" / "some" / "resource" / "one"
      val request =
        requestFrom(path, Query("parents" -> "false", "self" -> "true"))
      when(aclsClient(request)).thenReturn(Future.successful(expected))
      client.getAcls("prefix" / "some" / "resource" / "one", self = true).futureValue shouldEqual expected
    }

    "return expected acls whenever the caller is anonymous" in {
      implicit val anonCaller = AnonymousCaller
      val expected =
        FullAccessControlList((AuthenticatedRef(None), Path("/acls/prefix/some/resource/two"), Permissions(Read)))
      val path    = "acls" / "prefix" / "some" / "resource" / "two"
      val request = requestFrom(path, Query("parents" -> "false", "self" -> "false"))
      when(aclsClient(request)).thenReturn(Future.successful(expected))
      client.getAcls("prefix" / "some" / "resource" / "two").futureValue shouldEqual expected
    }
  }

  private def requestFrom(path: Path, query: Query)(implicit credentials: Option[AuthToken]): HttpRequest = {
    val request =
      Get(iamUri.value.append(path).withQuery(query)).withEntity(HttpEntity.empty(ContentTypes.NoContentType))
    credentials.map(request.addCredentials(_)).getOrElse(request)
  }

}
