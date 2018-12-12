package ch.epfl.bluebrain.nexus.iam.permissions

import java.time.Instant

import akka.stream.ActorMaterializer
import cats.effect.{Clock, ContextShift, IO, Timer}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.acls.Acls
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{HttpConfig, PermissionsConfig}
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.permissions.PermissionsRejection._
import ch.epfl.bluebrain.nexus.iam.types.IamError.AccessDenied
import ch.epfl.bluebrain.nexus.iam.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.iam.types.{Caller, Permission, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import org.mockito.IdiomaticMockito
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

//noinspection TypeAnnotation,NameBooleanParameters
class PermissionsSpec
    extends ActorSystemFixture("PermissionsSpec", true)
    with Matchers
    with IOEitherValues
    with IOOptionValues
    with Randomness
    with IdiomaticMockito {

  val appConfig: AppConfig           = Settings(system).appConfig
  implicit val http: HttpConfig      = appConfig.http
  implicit val pc: PermissionsConfig = appConfig.permissions

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

  val (macls, acls) = {
    val m = mock[Acls[IO]]
    m.hasPermission(Path./, read, ancestors = false)(caller) shouldReturn IO.pure(true)
    m.hasPermission(Path./, write, ancestors = false)(caller) shouldReturn IO.pure(true)
    (m, () => IO.pure(m))
  }

  val minimum = Set(
    Permission.unsafe("acls/write"),
    Permission.unsafe("permissions/read"),
    Permission.unsafe("permissions/write"),
    Permission.unsafe("realms/read"),
    Permission.unsafe("realms/write"),
  )

  val perm1: Permission = Permission.unsafe(genString())
  val perm2: Permission = Permission.unsafe(genString())
  val perm3: Permission = Permission.unsafe(genString())
  val perm4: Permission = Permission.unsafe(genString())

  val epoch: Instant = Instant.EPOCH

  "The Permissions API" should {
    val perms = Permissions(acls).ioValue
    "return the minimum permissions" in {
      perms.effectivePermissions.ioValue shouldEqual minimum
    }
    "return the minimum permissions resource" in {
      perms.fetch.ioValue shouldEqual ResourceF(id, 0L, types, false, epoch, Anonymous, epoch, Anonymous, minimum)
    }
    "fail to delete minimum when initial" in {
      perms.delete(0L).rejected[CannotDeleteMinimumCollection.type]
    }
    "fail to subtract with incorrect rev" in {
      perms.subtract(Set(perm1), 1L).rejected[IncorrectRev].rev shouldEqual 1L
    }
    "fail to subtract from minimum" in {
      perms.subtract(Set(perm1), 0L).rejected[CannotSubtractFromMinimumCollection].permissions shouldEqual minimum
    }
    "fail to subtract undefined permissions" in {
      perms.append(Set(perm1)).ioValue
      perms.subtract(Set(perm2), 1L).rejected[CannotSubtractUndefinedPermissions].permissions shouldEqual Set(perm2)
    }
    "fail to subtract empty permissions" in {
      perms.subtract(Set(), 1L).rejected[CannotSubtractEmptyCollection.type]
    }
    "fail to subtract from minimum collection" in {
      perms.subtract(Set(read), 1L).rejected[CannotSubtractFromMinimumCollection].permissions shouldEqual minimum
    }
    "subtract a permission" in {
      perms.subtract(Set(perm1), 1L).accepted
      perms.effectivePermissions.ioValue shouldEqual minimum
    }
    "fail to append with incorrect rev" in {
      perms.append(Set(perm1)).rejected[IncorrectRev].rev shouldEqual 0L
    }
    "append permissions" in {
      perms.append(Set(perm1, perm2), 2L).accepted
      perms.effectivePermissions.ioValue shouldEqual (minimum ++ Set(perm1, perm2))
    }
    "fail to append duplicate permissions" in {
      perms.append(Set(perm2), 3L).rejected[CannotAppendEmptyCollection.type]
    }
    "fail to append empty permissions" in {
      perms.append(Set(), 3L).rejected[CannotAppendEmptyCollection.type]
    }
    "fail to replace with incorrect rev" in {
      perms.replace(Set(perm3), 1L).rejected[IncorrectRev].rev shouldEqual 1L
    }
    "fail to replace with empty permissions" in {
      perms.replace(Set(), 3L).rejected[CannotReplaceWithEmptyCollection.type]
    }
    "fail to replace with subset of minimum" in {
      perms.replace(Set(read), 3L).rejected[CannotReplaceWithEmptyCollection.type]
    }
    "replace non minimum" in {
      perms.replace(Set(perm3, perm4), 3L).accepted
      perms.effectivePermissions.ioValue shouldEqual (pc.minimum ++ Set(perm3, perm4))
    }
    "fail to delete with incorrect rev" in {
      perms.delete(2L).rejected[IncorrectRev].rev shouldEqual 2L
    }
    "delete permissions" in {
      perms.delete(4L).accepted
      perms.effectivePermissions.ioValue shouldEqual minimum
    }
    "fail to delete minimum permissions" in {
      perms.delete(5L).rejected[CannotDeleteMinimumCollection.type]
    }

    // NO PERMISSIONS BEYOND THIS LINE

    "fail to fetch when no permissions" in {
      macls.hasPermission(Path./, read, ancestors = false)(caller) shouldReturn IO.pure(false)
      macls.hasPermission(Path./, write, ancestors = false)(caller) shouldReturn IO.pure(false)
      perms.fetch.failed[AccessDenied] shouldEqual AccessDenied(id, read)
      perms.effectivePermissions.failed[AccessDenied] shouldEqual AccessDenied(id, read)
    }
    "fail to append when no permissions" in {
      perms.append(Set(perm3)).failed[AccessDenied] shouldEqual AccessDenied(id, write)
    }
    "fail to subtract when no permissions" in {
      perms.subtract(Set(perm3), 1L).failed[AccessDenied] shouldEqual AccessDenied(id, write)
    }
    "fail to replace when no permissions" in {
      perms.replace(Set(perm3), 1L).failed[AccessDenied] shouldEqual AccessDenied(id, write)
    }
    "fail to delete when no permissions" in {
      perms.delete(1L).failed[AccessDenied] shouldEqual AccessDenied(id, write)
    }
  }
}
