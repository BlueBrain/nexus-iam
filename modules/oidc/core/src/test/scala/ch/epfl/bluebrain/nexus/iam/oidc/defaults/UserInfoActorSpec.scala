package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.UserInfoActor.Protocol._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class UserInfoActorSpec
    extends TestKit(ActorSystem("UserInfoActorSpec"))
    with WordSpecLike
    with Matchers
    with ImplicitSender
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "A UserInfoActor" should {
    def createUserInfoActor() =
      TestActorRef(new UserInfoActor {
        override protected def initiateStop(): Unit =
          context.stop(self)
      })
    val uuid = UUID.randomUUID().toString

    "return no info for unknown token" in {
      createUserInfoActor() ! GetInfo(uuid)
      expectMsg(Info(`uuid`, None))
    }

    "return the info for a set token" in {
      val target = createUserInfoActor()
      val userInfo =
        UserInfo("sub", "name", "preferredUsername", "givenName", "familyName", "email@email.com", Set.empty)
      target ! SetInfo(uuid, userInfo)
      target ! GetInfo(uuid)
      expectMsg(Info(`uuid`, Some(`userInfo`)))
    }
  }

  "A UserInfoActor sha function" should {
    "compute the SHA 256 correctly" in {
      import UserInfoActor.sha256
      sha256("my well defined token") shouldEqual "b0be9dd17b7eef1d7bb02f9195745a89570bb601d03b405f07754a91d6410ed1"
    }
  }
}
