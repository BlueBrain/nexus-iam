package ch.epfl.bluebrain.nexus.iam.client

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{AuthenticatedRef, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.commons.types.identity.{AuthenticatedUser, User}
import ch.epfl.bluebrain.nexus.iam.client.Caller._
import ch.epfl.bluebrain.nexus.iam.client.types.Permission._
import ch.epfl.bluebrain.nexus.iam.client.types.{FullAccessControlList, Permissions}
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._
import ch.epfl.bluebrain.nexus.service.http.UriOps._
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class IamClientSpec
    extends TestKit(ActorSystem("IamClientSpec"))
    with WordSpecLike
    with Matchers
    with ScalaFutures
    with BeforeAndAfter
    with MockitoSugar {

  private implicit val ec     = system.dispatcher
  private implicit val iamUri = IamUri("http://localhost:8080")
  private val credentials     = OAuth2BearerToken("validToken")
  private val authUser: User = AuthenticatedUser(
    Set(GroupRef("BBP", "group1"), GroupRef("BBP", "group2"), UserRef("realm", "f:someUUID:username")))
  private val authUserWithFilteredGroups: User = AuthenticatedUser(
    Set(GroupRef("BBP", "group1"), UserRef("realm", "f:someUUID:username")))

  implicit val aclsClient = mock[HttpClient[Future, FullAccessControlList]]
  implicit val userClient = mock[HttpClient[Future, User]]

  val client = IamClient()

  before {
    Mockito.reset(aclsClient)
    Mockito.reset(userClient)
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(5 seconds, 200 milliseconds)

  "An IamClient" should {

    "return unathorized whenever the token is wrong" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(OAuth2BearerToken("invalidToken"))
      val path                                     = "oauth2" / "user"
      val request                                  = requestFrom(path, Query("filterGroups" -> "false"))
      when(userClient(request)).thenReturn(Future.failed(UnauthorizedAccess))

      ScalaFutures.whenReady(client.getCaller().failed, Timeout(patienceConfig.timeout)) { e =>
        e shouldBe a[UnauthorizedAccess.type]
      }
    }

    "return anonymous caller whenever there is no token provided" in {
      implicit val cred: Option[OAuth2BearerToken] = None
      client.getCaller().futureValue shouldEqual AnonymousCaller()
    }

    "return an authenticated caller whenever the token provided is correct" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(credentials)
      val path                                     = "oauth2" / "user"
      val request                                  = requestFrom(path, Query("filterGroups" -> "false"))
      when(userClient(request)).thenReturn(Future.successful(authUser))
      client.getCaller().futureValue shouldEqual AuthenticatedCaller(credentials, authUser)
    }

    "return an authenticated caller with filtered groups" in {
      implicit val cred: Option[OAuth2BearerToken] = Some(credentials)
      val path                                     = "oauth2" / "user"
      val request                                  = requestFrom(path, Query("filterGroups" -> "true"))
      when(userClient(request)).thenReturn(Future.successful(authUserWithFilteredGroups))
      client.getCaller(filterGroups = true).futureValue shouldEqual AuthenticatedCaller(credentials,
                                                                                        authUserWithFilteredGroups)
    }

    "return expected acls whenever the caller is authenticated" in {
      implicit val caller = AuthenticatedCaller(credentials, authUser)
      val expected = FullAccessControlList(
        (GroupRef("BBP", "group1"), Path("/acls/prefix/some/resource/one"), Permissions(Own, Read, Write)))
      val path = "acls" / "prefix" / "some" / "resource" / "one"
      val request =
        requestFrom(path, Query("parents" -> "false", "self" -> "true"))
      when(aclsClient(request)).thenReturn(Future.successful(expected))
      client.getAcls("prefix" / "some" / "resource" / "one", self = true).futureValue shouldEqual expected
    }

    "return expected acls whenever the caller is anonymous" in {
      implicit val anonCaller = AnonymousCaller()
      val expected =
        FullAccessControlList((AuthenticatedRef(None), Path("/acls/prefix/some/resource/two"), Permissions(Read)))
      val path    = "acls" / "prefix" / "some" / "resource" / "two"
      val request = requestFrom(path, Query("parents" -> "false", "self" -> "false"))
      when(aclsClient(request)).thenReturn(Future.successful(expected))
      client.getAcls("prefix" / "some" / "resource" / "two").futureValue shouldEqual expected
    }
  }

  private def requestFrom(path: Path, query: Query)(implicit credentials: Option[OAuth2BearerToken]): HttpRequest = {
    val request =
      Get(iamUri.value.append(path).withQuery(query)).withEntity(HttpEntity.empty(ContentTypes.NoContentType))
    credentials.map(request.addCredentials).getOrElse(request)
  }

}