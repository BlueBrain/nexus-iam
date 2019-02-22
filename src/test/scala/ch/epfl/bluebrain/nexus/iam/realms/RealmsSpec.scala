package ch.epfl.bluebrain.nexus.iam.realms

import java.time.Instant
import java.util.Date

import akka.http.scaladsl.client.RequestBuilding._
import akka.stream.ActorMaterializer
import cats.effect.{Clock, ContextShift, IO, Timer}
import ch.epfl.bluebrain.nexus.commons.test.ActorSystemFixture
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.acls.Acls
import ch.epfl.bluebrain.nexus.iam.auth.AccessToken
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{HttpConfig, RealmsConfig}
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.realms.RealmRejection._
import ch.epfl.bluebrain.nexus.iam.realms.WellKnownSpec._
import ch.epfl.bluebrain.nexus.iam.types.IamError.AccessDenied
import ch.epfl.bluebrain.nexus.iam.types.Identity.{Anonymous, Authenticated, Group, User}
import ch.epfl.bluebrain.nexus.iam.types.{Caller, IamError, Label, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.{Path, Url}
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.mockito.ArgumentMatchersSugar._
import org.mockito.IdiomaticMockito
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

//noinspection TypeAnnotation,NameBooleanParameters,RedundantDefaultArgument
class RealmsSpec
    extends ActorSystemFixture("RealmsSpec", true)
    with Matchers
    with IOEitherValues
    with IOOptionValues
    with Randomness
    with IdiomaticMockito {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(3 seconds, 50 milliseconds)

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
    m.apply(Get(openIdUrlString)) shouldReturn IO.pure(fullOpenIdConfig)
    m.apply(Get(jwksUrlString)) shouldReturn IO.pure(validJwks)
    m.apply(Get(deprUrlString)) shouldReturn IO.pure(deprecatedOpenIdConfig)
    m
  }

  val (macls, acls) = {
    val m = mock[Acls[IO]]
    m.hasPermission(isA[Path], read, isA[Boolean])(caller) shouldReturn IO.pure(true)
    m.hasPermission(isA[Path], write, isA[Boolean])(caller) shouldReturn IO.pure(true)
    (m, IO.pure(m))
  }

  val first      = Label.unsafe("first")
  val firstName  = "The First"
  val logoUrl    = Url("http://localhost/some/logo").right.value
  val second     = Label.unsafe("second")
  val secondName = "The Second"
  val depr       = Label.unsafe("deprecated")
  val deprName   = "The deprecated realm"

  def token(
      subject: String,
      exp: Date = Date.from(Instant.now().plusSeconds(3600)),
      nbf: Date = Date.from(Instant.now().minusSeconds(3600)),
      groups: Option[Set[String]] = None,
      useCommas: Boolean = false,
      preferredUsername: Option[String] = None,
  ): AccessToken = {
    val signer = new RSASSASigner(privateKey)
    val csb = new JWTClaimsSet.Builder()
      .issuer(issuer)
      .subject(subject)
      .expirationTime(exp)
      .notBeforeTime(nbf)

    groups.map { set =>
      if (useCommas) csb.claim("groups", set.mkString(","))
      else csb.claim("groups", set.toArray)
    }

    preferredUsername.map { pu =>
      csb.claim("preferred_username", pu)
    }

    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), csb.build())
    jwt.sign(signer)
    AccessToken(jwt.serialize())
  }

  "The Realms API" should {
    val realms = Realms[IO](acls).ioValue
    "create a new realm" in {
      realms.create(first, firstName, openIdUrl, None).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        1L,
        types,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(
          ActiveRealm(
            first,
            firstName,
            openIdUrl,
            issuer,
            grantTypes,
            None,
            authorizationUrl,
            tokenUrl,
            userInfoUrl,
            Some(revocationUrl),
            Some(endSessionUrl),
            Set(publicKeyJson)
          ))
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
          instant,
          Anonymous,
          instant,
          Anonymous,
          Right(
            ActiveRealm(
              first,
              firstName,
              openIdUrl,
              issuer,
              grantTypes,
              None,
              authorizationUrl,
              tokenUrl,
              userInfoUrl,
              Some(revocationUrl),
              Some(endSessionUrl),
              Set(publicKeyJson)
            ))
        ),
        ResourceF(
          second.toIri(http.realmsIri),
          1L,
          types,
          instant,
          Anonymous,
          instant,
          Anonymous,
          Right(
            ActiveRealm(
              second,
              secondName,
              openIdUrl,
              issuer,
              grantTypes,
              None,
              authorizationUrl,
              tokenUrl,
              userInfoUrl,
              Some(revocationUrl),
              Some(endSessionUrl),
              Set(publicKeyJson)
            ))
        )
      )
    }
    "update an existing realm" in {
      realms.update(first, 1L, firstName + "x", openIdUrl, Some(logoUrl)).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        2L,
        types,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(
          ActiveRealm(
            first,
            firstName + "x",
            openIdUrl,
            issuer,
            grantTypes,
            Some(logoUrl),
            authorizationUrl,
            tokenUrl,
            userInfoUrl,
            Some(revocationUrl),
            Some(endSessionUrl),
            Set(publicKeyJson)
          ))
      )
    }
    "fetch a realm at revision" in {
      realms.fetch(first, 1L).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        1L,
        types,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(
          ActiveRealm(
            first,
            firstName,
            openIdUrl,
            issuer,
            grantTypes,
            None,
            authorizationUrl,
            tokenUrl,
            userInfoUrl,
            Some(revocationUrl),
            Some(endSessionUrl),
            Set(publicKeyJson)
          ))
      )
    }
    "deprecate an existing realm" in {
      realms.deprecate(first, 2L).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        3L,
        types,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Left(DeprecatedRealm(first, firstName + "x", openIdUrl, Some(logoUrl)))
      )
    }
    "fail to deprecate twice a realm" in {
      realms.deprecate(first, 3L).rejected[RealmAlreadyDeprecated]
    }
    "return none for a wrong revision" in {
      realms.fetch(first, 9999L).ioValue shouldEqual None
    }
    "un-deprecate a realm" in {
      realms.update(first, 3L, firstName, openIdUrl, Some(logoUrl)).accepted
      realms.fetch(first).some shouldEqual ResourceF(
        first.toIri(http.realmsIri),
        4L,
        types,
        instant,
        Anonymous,
        instant,
        Anonymous,
        Right(
          ActiveRealm(
            first,
            firstName,
            openIdUrl,
            issuer,
            grantTypes,
            Some(logoUrl),
            authorizationUrl,
            tokenUrl,
            userInfoUrl,
            Some(revocationUrl),
            Some(endSessionUrl),
            Set(publicKeyJson)
          ))
      )
    }
    "fail to update a realm with incorrect revision" in {
      realms.update(first, 10L, firstName, openIdUrl, Some(logoUrl)).rejected[IncorrectRev]
    }
    "fail to update a realm that does not exist" in {
      realms.update(Label.unsafe("blah"), 10L, firstName, openIdUrl, Some(logoUrl)).rejected[RealmNotFound]
    }
    "fail to deprecate a realm with incorrect revision" in {
      realms.deprecate(first, 10L).rejected[IncorrectRev]
    }
    "fail to deprecate a realm that does not exist" in {
      realms.deprecate(Label.unsafe("blah"), 10L).rejected[RealmNotFound]
    }

    "correctly extract the caller" when {
      val subject      = "sub"
      val preferred    = "preferred"
      val user         = User(subject, first.value)
      val groupStrings = Set("g1", "g2")
      val groups       = groupStrings.map(str => Group(str, first.value))
      val auth         = Authenticated(first.value)
      "the claimset contains no groups" in {
        val user = User(subject, first.value)
        realms.caller(token(subject)).ioValue shouldEqual Caller(user, Set(user, Anonymous, auth))
      }
      "the claimset contains comma separated group values" in {
        val expected = Caller(user, Set(user, Anonymous, auth) ++ groups)
        realms.caller(token(subject, groups = Some(groupStrings), useCommas = true)).ioValue shouldEqual expected
      }
      "the claimset contains array group values" in {
        val expected = Caller(user, Set(user, Anonymous, auth) ++ groups)
        realms.caller(token(subject, groups = Some(groupStrings), useCommas = false)).ioValue shouldEqual expected
      }
      "the claimset contains a preferred_username entry" in {
        val user     = User(preferred, first.value)
        val expected = Caller(user, Set(user, Anonymous, auth))
        realms.caller(token(subject, preferredUsername = Some(preferred))).ioValue shouldEqual expected
      }
    }

    "fail to extract the caller" when {
      val signer = new RSASSASigner(privateKey)
      "the token is invalid" in {
        realms.caller(AccessToken("blah")).failed[IamError.InvalidAccessToken]
      }
      "the token doesn't contain an issuer" in {
        val csb = new JWTClaimsSet.Builder()
          .subject("sub")
          .expirationTime(Date.from(Instant.now().plusSeconds(3600)))

        val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), csb.build())
        jwt.sign(signer)
        realms.caller(AccessToken(jwt.serialize())).failed[IamError.InvalidAccessToken]
      }
      "the token contains an unknown issuer" in {
        val csb = new JWTClaimsSet.Builder()
          .subject("sub")
          .issuer("blah")
          .expirationTime(Date.from(Instant.now().plusSeconds(3600)))

        val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), csb.build())
        jwt.sign(signer)
        realms.caller(AccessToken(jwt.serialize())).failed[IamError.InvalidAccessToken]
      }
      "the token doesn't contain a subject" in {
        val csb = new JWTClaimsSet.Builder()
          .issuer(issuer)
          .expirationTime(Date.from(Instant.now().plusSeconds(3600)))

        val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), csb.build())
        jwt.sign(signer)
        realms.caller(AccessToken(jwt.serialize())).failed[IamError.InvalidAccessToken]
      }
      "the token is expired" in {
        val exp = Date.from(Instant.now().minusSeconds(3600))
        realms.caller(token("sub", exp = exp)).failed[IamError.InvalidAccessToken]
      }
      "the token is not yet valid" in {
        val nbf = Date.from(Instant.now().plusSeconds(3600))
        realms.caller(token("sub", nbf = nbf)).failed[IamError.InvalidAccessToken]
      }
      "the realm for which the issuer matches is deprecated" in {
        realms.create(depr, deprName, openIdUrl, None).accepted
        realms.deprecate(depr, 1L).accepted
        val csb = new JWTClaimsSet.Builder()
          .subject("sub")
          .issuer("deprecated")
          .expirationTime(Date.from(Instant.now().plusSeconds(3600)))

        val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), csb.build())
        jwt.sign(signer)
        realms.caller(AccessToken(jwt.serialize())).failed[IamError.InvalidAccessToken]
      }
      "the signature is invalid" in {
        val (otherkid, otherprivateKey) = {
          val rsaJWK = new RSAKeyGenerator(2048)
            .keyID("123")
            .generate()
          (rsaJWK.getKeyID, rsaJWK.toRSAPrivateKey)
        }
        val csb = new JWTClaimsSet.Builder()
          .subject("sub")
          .issuer(issuer)
          .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
        val jwt    = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(otherkid).build(), csb.build())
        val signer = new RSASSASigner(otherprivateKey)
        jwt.sign(signer)
        realms.caller(AccessToken(jwt.serialize())).failed[IamError.InvalidAccessToken]
      }
    }

    // NO PERMISSIONS BEYOND THIS LINE
    "fail to create a realm with no permissions" in {
      macls.hasPermission(isA[Path], read, isA[Boolean])(caller) shouldReturn IO.pure(false)
      macls.hasPermission(isA[Path], write, isA[Boolean])(caller) shouldReturn IO.pure(false)
      realms.create(first, firstName, openIdUrl, None).failed[AccessDenied]
    }

    "fail to update a realm with no permissions" in {
      realms.update(first, 10L, firstName, openIdUrl, Some(logoUrl)).failed[AccessDenied]
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
