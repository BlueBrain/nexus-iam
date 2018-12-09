package ch.epfl.bluebrain.nexus.iam.realms

import java.time.Instant

import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.ActorMaterializer
import cats.effect.{Clock, ContextShift, IO, Timer}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.acls.Acls
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{HttpConfig, RealmsConfig}
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.WellKnownSpec._
import ch.epfl.bluebrain.nexus.iam.types.IamError.AccessDenied
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.{Caller, Label, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.{Path, Url}
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import org.mockito.ArgumentMatchersSugar._
import org.mockito.IdiomaticMockito
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.MILLISECONDS

//noinspection TypeAnnotation,NameBooleanParameters
class RealmsSpec
    extends ActorSystemFixture("RealmsSpec", true)
    with Matchers
    with IOEitherValues
    with IOOptionValues
    with Randomness
    with IdiomaticMockito {

  val appConfig: AppConfig      = Settings(system).appConfig
  implicit val http: HttpConfig = appConfig.http
  implicit val rc: RealmsConfig = appConfig.realms

  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ctx: ContextShift[IO]  = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]       = IO.timer(ExecutionContext.global)
  implicit val caller: Caller         = Caller.anonymous

  val instant: Instant = Instant.ofEpochMilli(5l)
  implicit val clock: Clock[IO] = {
    val m = mock[Clock[IO]]
    m.realTime(MILLISECONDS) shouldReturn IO.pure(instant.toEpochMilli)
    m
  }

  implicit val httpClient: HttpJsonClient[IO] = {
    val m = mock[HttpJsonClient[IO]]
    m.apply(Get(openIdUrlString)) shouldReturn IO.pure(validOpenIdConfig)
    m.apply(Get(jwksUrlString)) shouldReturn IO.pure(validJwks)
    m
  }

  val (macls, acls) = {
    val m = mock[Acls[IO]]
    m.hasPermission(isA[Path], read, isA[Boolean])(caller) shouldReturn IO.pure(true)
    m.hasPermission(isA[Path], write, isA[Boolean])(caller) shouldReturn IO.pure(true)
    (m, () => IO.pure(m))
  }

  val first      = Label.unsafe("first")
  val firstName  = "The First"
  val logoUrl    = Url("http://localhost/some/logo").right.value
  val second     = Label.unsafe("second")
  val secondName = "The Second"

  "The Realms API" should {
    val realms = Realms[IO](acls).ioValue
    "create a new realm" in {
      realms.create(first, firstName, openIdUrl, None).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        1L,
        types,
        false,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(ActiveRealm(firstName, openIdUrl, issuer, grantTypes, None, Set(validKeyJson)))
      )
    }

    "fail to create an existing realm" in {
      realms.create(first, firstName, openIdUrl, None).rejected[RealmAlreadyExists]
    }
    "list existing realms" in {
      realms.create(second, secondName, openIdUrl, None).accepted
      realms.list.ioValue.toSet shouldEqual Set(
        ResourceF(
          first.toIri(http.realmsIri),
          1L,
          types,
          false,
          instant,
          Anonymous,
          instant,
          Anonymous,
          Right(ActiveRealm(firstName, openIdUrl, issuer, grantTypes, None, Set(validKeyJson)))
        ),
        ResourceF(
          second.toIri(http.realmsIri),
          1L,
          types,
          false,
          instant,
          Anonymous,
          instant,
          Anonymous,
          Right(ActiveRealm(secondName, openIdUrl, issuer, grantTypes, None, Set(validKeyJson)))
        )
      )
    }
    "update an existing realm" in {
      realms.update(first, 1L, Some(firstName + "x"), Some(openIdUrl), Some(logoUrl)).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        2L,
        types,
        false,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(ActiveRealm(firstName + "x", openIdUrl, issuer, grantTypes, Some(logoUrl), Set(validKeyJson)))
      )
    }
    // TODO: find out why the in memory implementation doesn't return the events
    "fetch a realm at revision" ignore {
      realms.fetch(first, 1L).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        1L,
        types,
        false,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(ActiveRealm(firstName, openIdUrl, issuer, grantTypes, None, Set(validKeyJson)))
      )
    }
    "deprecate an existing realm" in {
      realms.deprecate(first, 2L).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        3L,
        types,
        true,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Left(DeprecatedRealm(firstName + "x", openIdUrl, Some(logoUrl)))
      )
    }
    "fail to deprecate twice a realm" in {
      realms.deprecate(first, 3L).rejected[RealmAlreadyDeprecated]
    }
    "un-deprecate a realm" in {
      realms.update(first, 3L, Some(firstName), Some(openIdUrl), Some(logoUrl)).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        4L,
        types,
        false,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(ActiveRealm(firstName, openIdUrl, issuer, grantTypes, Some(logoUrl), Set(validKeyJson)))
      )
    }
    "update a realm with no changes" in {
      realms.update(first, 4L, None, None, None).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        5L,
        types,
        false,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(ActiveRealm(firstName, openIdUrl, issuer, grantTypes, Some(logoUrl), Set(validKeyJson)))
      )
    }
    "fail to update a realm with incorrect revision" in {
      realms.update(first, 10L, Some(firstName), Some(openIdUrl), Some(logoUrl)).rejected[IncorrectRev]
    }
    "fail to update a realm that does not exist" in {
      realms.update(Label.unsafe("blah"), 10L, Some(firstName), Some(openIdUrl), Some(logoUrl)).rejected[RealmNotFound]
    }
    "fail to deprecate a realm with incorrect revision" in {
      realms.deprecate(first, 10L).rejected[IncorrectRev]
    }
    "fail to deprecate a realm that does not exist" in {
      realms.deprecate(Label.unsafe("blah"), 10L).rejected[RealmNotFound]
    }

    // NO PERMISSIONS BEYOND THIS LINE
    "fail to create a realm with no permissions" in {
      macls.hasPermission(isA[Path], read, isA[Boolean])(caller) shouldReturn IO.pure(false)
      macls.hasPermission(isA[Path], write, isA[Boolean])(caller) shouldReturn IO.pure(false)
      realms.create(first, firstName, openIdUrl, None).failed[AccessDenied]
    }

    "fail to update a realm with no permissions" in {
      realms.update(first, 10L, Some(firstName), Some(openIdUrl), Some(logoUrl)).failed[AccessDenied]
    }

    "fail to deprecate a realm with no permissions" in {
      realms.deprecate(first, 10L).failed[AccessDenied]
    }

    "fail to fetch a realm with no permissions" in {
      realms.fetch(first).failed[AccessDenied]
    }

    "fail to fetch a realm revision with no permissions" in {
      realms.fetch(first, 2L).failed[AccessDenied]
    }

    "fail to list realms with no permissions" in {
      realms.list.failed[AccessDenied]
    }
  }
}
