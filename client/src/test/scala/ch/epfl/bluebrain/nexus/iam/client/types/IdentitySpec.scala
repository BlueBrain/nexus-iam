package ch.epfl.bluebrain.nexus.iam.client.types

import ch.epfl.bluebrain.nexus.commons.test.{EitherValues, Resources}
import ch.epfl.bluebrain.nexus.iam.client.config.IamClientConfig
import ch.epfl.bluebrain.nexus.iam.client.types.Identity._
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.implicits._
import io.circe.syntax._
import org.scalatest.{Inspectors, OptionValues}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class IdentitySpec
    extends AnyWordSpecLike
    with Matchers
    with Inspectors
    with OptionValues
    with Resources
    with EitherValues {

  "An identity" should {
    implicit val config: IamClientConfig =
      IamClientConfig(url"http://nexus.example.com", url"http://internal.nexus.example.com", "v1")
    val user          = User("mysubject", "myrealm")
    val group         = Group("mygroup", "myrealm")
    val authenticated = Authenticated("myrealm")

    "be created from ids" in {
      val cases = List[(AbsoluteIri, Identity)](
        url"http://nexus.example.com/v1/realms/myrealm/users/mysubject" -> user,
        url"https://random.com/v1/realms/myrealm/users/mysubject"       -> user,
        url"http://nexus.example.com/v1/realms/myrealm/groups/mygroup"  -> group,
        url"https://random.com/v1/realms/myrealm/groups/mygroup"        -> group,
        url"http://nexus.example.com/v1/realms/myrealm/authenticated"   -> authenticated,
        url"https://random.com/v1/realms/myrealm/authenticated"         -> authenticated,
        url"http://nexus.example.com/v1/anonymous"                      -> Anonymous,
        url"https://random.com/v1/anonymous"                            -> Anonymous
      )
      forAll(cases) {
        case (iri, identity) => Identity(iri).value shouldEqual identity
      }
    }

    "converted to Json" in {
      val userJson          = jsonContentOf("/identities/produce/user.json")
      val groupJson         = jsonContentOf("/identities/produce/group.json")
      val authenticatedJson = jsonContentOf("/identities/produce/authenticated.json")
      val anonymousJson     = jsonContentOf("/identities/produce/anonymous.json")

      val cases =
        List(user -> userJson, group -> groupJson, Anonymous -> anonymousJson, authenticated -> authenticatedJson)

      forAll(cases) {
        case (model: Subject, json) =>
          model.asJson shouldEqual json
          (model: Identity).asJson shouldEqual json
        case (model: Identity, json) => model.asJson shouldEqual json
      }
    }
    "convert from Json" in {
      val userJson          = jsonContentOf("/identities/consume/user.json")
      val groupJson         = jsonContentOf("/identities/consume/group.json")
      val authenticatedJson = jsonContentOf("/identities/consume/authenticated.json")
      val anonymousJson     = jsonContentOf("/identities/consume/anonymous.json")
      val cases =
        List(user -> userJson, group -> groupJson, Anonymous -> anonymousJson, authenticated -> authenticatedJson)
      forAll(cases) {
        case (model: Subject, json) =>
          json.as[Subject].rightValue shouldEqual model
          json.as[Identity].rightValue shouldEqual (model: Identity)
        case (model: Identity, json) => json.as[Identity].rightValue shouldEqual model
      }
    }
  }
}
