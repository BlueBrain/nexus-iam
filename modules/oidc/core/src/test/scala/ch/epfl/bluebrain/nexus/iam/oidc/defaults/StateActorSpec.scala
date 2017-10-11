package ch.epfl.bluebrain.nexus.iam.oidc.defaults

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import ch.epfl.bluebrain.nexus.iam.oidc.defaults.StateActor.Protocol._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class StateActorSpec
    extends TestKit(ActorSystem("StateActorSpec"))
    with WordSpecLike
    with Matchers
    with ImplicitSender
    with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "A StateActor" should {
    def createStateActor() =
      TestActorRef(new StateActor {
        override protected def initiateStop(): Unit =
          context.stop(self)
      })
    val uuid       = UUID.randomUUID().toString
    val redirectTo = Uri(s"http://localhost/$uuid")

    "echo the state derived from an externally generated uuid" in {
      createStateActor() ! GenState(uuid, Some(redirectTo))
      expectMsg(AuthState(`uuid`, Some(`redirectTo`)))
    }

    "fail to validate an unknown state" in {
      val target = createStateActor()
      target ! GenState(uuid, Some(redirectTo))
      expectMsgType[AuthState]
      val invalidUuid = uuid + "a"
      target ! ValidateState(invalidUuid)
      expectMsg(InvalidStateReference(`invalidUuid`))
    }

    "fail to validate without a state" in {
      val invalidUuid = uuid + "a"
      createStateActor() ! ValidateState(invalidUuid)
      expectMsg(InvalidStateReference(`invalidUuid`))
    }

    "validate a known state" in {
      val target = createStateActor()
      target ! GenState(uuid, Some(redirectTo))
      expectMsg(AuthState(`uuid`, Some(`redirectTo`)))
      target ! ValidateState(uuid)
      expectMsg(AuthState(`uuid`, Some(`redirectTo`)))
    }
  }
}
