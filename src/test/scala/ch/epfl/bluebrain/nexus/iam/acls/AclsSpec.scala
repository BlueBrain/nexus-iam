package ch.epfl.bluebrain.nexus.iam.acls

import java.time.{Clock, Instant, ZoneId}

import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO, Timer}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.test.io.{IOEitherValues, IOOptionValues}
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{AclsConfig, HttpConfig, PermissionsConfig}
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.iam.permissions.Permissions
import ch.epfl.bluebrain.nexus.iam.types.IamError.AccessDenied
import ch.epfl.bluebrain.nexus.iam.types.Identity._
import ch.epfl.bluebrain.nexus.iam.types._
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import ch.epfl.bluebrain.nexus.rdf.Iri.{AbsoluteIri, Path}
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.service.test.ActorSystemFixture
import org.mockito.IdiomaticMockito
import org.scalatest._

import scala.concurrent.ExecutionContext
import scala.util.Random

//noinspection TypeAnnotation,NameBooleanParameters
class AclsSpec
    extends ActorSystemFixture("AclsSpec", true)
    with Matchers
    with IOEitherValues
    with IOOptionValues
    with Randomness
    with Inspectors
    with IdiomaticMockito {

  val appConfig: AppConfig      = Settings(system).appConfig
  val pc: PermissionsConfig     = appConfig.permissions
  implicit val http: HttpConfig = appConfig.http
  implicit val ac: AclsConfig   = appConfig.acls

  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ctx: ContextShift[IO]  = IO.contextShift(ExecutionContext.global)
  implicit val timer: Timer[IO]       = IO.timer(ExecutionContext.global)

  val (mperms, perms) = {
    val m = mock[Permissions[IO]]
    m.minimum shouldReturn appConfig.permissions.minimum
    (m, () => IO.pure(m))
  }

  private implicit val clock: Clock        = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private val acls                         = Acls[IO](perms).ioValue
  private val identities: List[Identity]   = List(User("sub", "realm"), Group("group", "realm"), Anonymous)
  private val permissions: Set[Permission] = List.fill(300)(Permission(genString(length = 6)).value).toSet
  private val instant                      = clock.instant()

  private def pathIriString(path: Path): String =
    s"${http.publicIri.asUri}/${http.prefix}/acls${path.asString}"

  trait Context {
    val createdBy: Subject = User("sub", "realm")
    implicit val caller    = Caller(createdBy, Set[Identity](Group("admin", "realm"), Anonymous))
    val path: Path         = genString(length = 4) / genString(length = 4)
    val id: AbsoluteIri    = Iri.absolute("http://127.0.0.1:8080/v1/acls/").right.value + path.asString
    val user1              = identities(genInt(max = 1))
    val user2              = identities.filterNot(_ == user1).head
    val permsUser1         = Random.shuffle(permissions).take(1 + genInt(max = 299))
    val permsUser2         = Random.shuffle(permissions).take(1 + genInt(max = 299))
    val acl                = AccessControlList(user1 -> permsUser1, user2 -> permsUser2)
  }

  trait AppendCtx extends Context {
    val permsUser1Append = Random.shuffle(permissions -- permsUser1).take(1 + genInt(max = 299))
    val permsUser2Append = Random.shuffle(permissions -- permsUser2).take(1 + genInt(max = 299))
    val aclAppend        = AccessControlList(user1 -> permsUser1Append, user2 -> permsUser2Append)
  }

  "The Acls surface API" when {

    "performing get operations" should {
      "fetch initial ACLs" in new Context {
        acls.fetch(/, self = true).some.value shouldEqual AccessControlList(Anonymous -> pc.minimum)
        acls.fetch(/, self = false).some.value shouldEqual AccessControlList(Anonymous -> pc.minimum)
      }

      "fetch initial with revision" in new Context {
        acls.fetch(/, 10L, self = true).some.value shouldEqual AccessControlList(Anonymous -> pc.minimum)
        acls.fetch(/, 10L, self = false).some.value shouldEqual AccessControlList(Anonymous -> pc.minimum)
      }

      "fetch other non existing ACLs" in new Context {
        acls.fetch(path, self = true).ioValue shouldEqual None
        acls.fetch(path, 10L, self = false).ioValue shouldEqual None
      }

      "fail to fetch by revision when using self=false without write permissions" in new Context {
        val failed = acls
          .fetch(path, 1L, self = false)(Caller(createdBy, Set(createdBy, Group("admin", genString()))))
          .failed[AccessDenied]
        failed.resource.asString shouldEqual pathIriString(path)
        failed.permission shouldEqual write
      }

      "fail to fetch when using self=false without write permissions" in new Context {
        val failed = acls
          .fetch(path, self = false)(Caller(createdBy, Set(createdBy, Group("admin", genString()))))
          .failed[AccessDenied]
        failed.resource.asString shouldEqual pathIriString(path)
        failed.permission shouldEqual write
      }
    }

    "performing replace operations" should {

      "reject when no parent acls/write permissions present" in new Context {
        val failed = acls
          .replace(path, 0L, acl)(Caller(createdBy, Set(createdBy, Group("admin", genString()))))
          .failed[AccessDenied]
        failed.resource.asString shouldEqual pathIriString(path)
        failed.permission shouldEqual write
      }

      "reject when wrong revision" in new Context {
        acls.replace(path, 1L, acl).ioValue shouldEqual Left(AclIncorrectRev(path, 1L))
      }

      "reject when empty permissions" in new Context {
        val emptyAcls = AccessControlList(user1 -> Set.empty, user2 -> permsUser2)
        acls.replace(path, 0L, emptyAcls).rejected[AclInvalidEmptyPermissions].path shouldEqual path
      }

      "successfully be created" in new Context {
        val metadata =
          ResourceMetadata(id, 1L, Set(nxv.AccessControlList), false, instant, createdBy, instant, createdBy)
        acls.replace(path, 0L, acl).accepted shouldEqual metadata
        acls.fetch(path, self = false).some shouldEqual metadata.map(_ => acl)
      }

      "successfully be updated" in new Context {
        acls.replace(path, 0L, acl).accepted shouldEqual
          ResourceMetadata(id, 1L, Set(nxv.AccessControlList), false, instant, createdBy, instant, createdBy)
        val replaced         = AccessControlList(user1 -> permsUser1)
        val updatedBy        = User(genString(), genString())
        val otherIds: Caller = Caller(updatedBy, Set(Group("admin", "realm"), updatedBy, Anonymous))
        val metadata =
          ResourceMetadata(id, 2L, Set(nxv.AccessControlList), false, instant, createdBy, instant, updatedBy)
        acls.replace(path, 1L, replaced)(otherIds).accepted shouldEqual metadata
        acls.fetch(path, self = false).some shouldEqual metadata.map(_ => replaced)
      }

      "reject when wrong revision after updated" in new Context {
        acls.replace(path, 0L, acl).accepted shouldEqual
          ResourceMetadata(id, 1L, Set(nxv.AccessControlList), false, instant, createdBy, instant, createdBy)

        val replaced = AccessControlList(user1 -> permsUser1)
        forAll(List(0L, 2L, 10L)) { rev =>
          acls.replace(path, rev, replaced).rejected[AclIncorrectRev] shouldEqual AclIncorrectRev(path, rev)
        }
      }
    }

    "performing append operations" should {

      "reject when trying to append the already existing ACL" in new Context {
        acls.replace(path, 0L, acl).accepted

        acls.append(path, 1L, acl).rejected[NothingToBeUpdated].path shouldEqual path
      }

      "reject when trying to append the partially already existing ACL" in new AppendCtx {
        val _      = acls.replace(path, 0L, acl).accepted
        val append = AccessControlList(user1 -> permsUser1)
        acls.append(path, 1L, append).rejected[NothingToBeUpdated].path shouldEqual path
      }

      "reject when no parent acls/write permissions present" in new AppendCtx {
        acls.replace(path, 0L, acl).accepted

        val failed = acls
          .append(path, 1L, aclAppend)(Caller(createdBy, Set(createdBy, Group("admin", genString()))))
          .failed[AccessDenied]
        failed.resource.asString shouldEqual pathIriString(path)
        failed.permission shouldEqual write
      }

      "reject when wrong revision" in new AppendCtx {
        acls.replace(path, 0L, acl).accepted

        forAll(List(0L, 2L, 10L)) { rev =>
          val rej = acls.append(path, rev, aclAppend).rejected[AclIncorrectRev]
          rej.path shouldEqual path
          rej.rev shouldEqual rev
        }
      }

      "reject when empty permissions" in new AppendCtx {
        acls.replace(path, 0L, acl).accepted

        val emptyAcls = AccessControlList(user1 -> Set.empty, user2 -> permsUser2)
        acls.append(path, 1L, emptyAcls).rejected[AclInvalidEmptyPermissions].path shouldEqual path
      }

      "successfully be appended" in new AppendCtx {
        acls.replace(path, 0L, acl).accepted

        val metadata =
          ResourceMetadata(id, 2L, Set(nxv.AccessControlList), false, instant, createdBy, instant, createdBy)
        acls.append(path, 1L, aclAppend).accepted shouldEqual metadata

        acls.fetch(path, self = false).some shouldEqual metadata.map(_ => aclAppend ++ acl)

      }
    }

    "performing subtract operations" should {

      "reject when trying to subtract nonExisting ACL" in new Context {
        acls.replace(path, 0L, acl).accepted
        val nonExisting =
          AccessControlList(user1 -> Set(Permission(genString()).value), user2 -> Set(Permission(genString()).value))
        acls.subtract(path, 1L, nonExisting).rejected[NothingToBeUpdated].path shouldEqual path
      }

      "reject when no parent acls/write permissions present" in new Context {
        acls.replace(path, 0L, acl).accepted

        val failed = acls
          .subtract(path, 1L, acl)(Caller(createdBy, Set(createdBy, Group("admin", genString()))))
          .failed[AccessDenied]
        failed.resource.asString shouldEqual pathIriString(path)
        failed.permission shouldEqual write
      }

      "reject when wrong revision" in new Context {
        acls.replace(path, 0L, acl).accepted

        forAll(List(0L, 2L, 10L)) { rev =>
          acls.subtract(path, rev, acl).rejected[AclIncorrectRev] shouldEqual AclIncorrectRev(path, rev)
        }
      }

      "reject when empty permissions" in new Context {
        acls.replace(path, 0L, acl).accepted

        val emptyAcls = AccessControlList(user1 -> Set.empty, user2 -> permsUser2)
        acls.subtract(path, 1L, emptyAcls).rejected[AclInvalidEmptyPermissions].path shouldEqual path
      }

      "successfully be subtracted" in new Context {
        acls.replace(path, 0L, acl).accepted

        val metadata =
          ResourceMetadata(id, 2L, Set(nxv.AccessControlList), false, instant, createdBy, instant, createdBy)
        acls.subtract(path, 1L, acl).accepted shouldEqual metadata

        acls.fetch(path, self = false).some shouldEqual metadata.map(_ => AccessControlList.empty)
      }
    }

    "performing delete operations" should {

      "reject when already deleted" in new Context {
        acls.replace(path, 0L, acl).accepted
        acls.subtract(path, 1L, acl).accepted
        acls.delete(path, 2L).rejected[AclIsEmpty].path shouldEqual path
      }

      "reject when no parent acls/write permissions present" in new Context {
        acls.replace(path, 0L, acl).accepted

        val failed = acls
          .delete(path, 1L)(Caller(createdBy, Set(createdBy, Group("admin", genString()))))
          .failed[AccessDenied]
        failed.resource.asString shouldEqual pathIriString(path)
        failed.permission shouldEqual write
      }

      "reject when wrong revision" in new Context {
        acls.replace(path, 0L, acl).accepted

        forAll(List(0L, 2L, 10L)) { rev =>
          acls.delete(path, rev).rejected[AclIncorrectRev] shouldEqual AclIncorrectRev(path, rev)
        }
      }

      "successfully be deleted" in new Context {
        acls.replace(path, 0L, acl).accepted

        val metadata =
          ResourceMetadata(id, 2L, Set(nxv.AccessControlList), false, instant, createdBy, instant, createdBy)
        acls.delete(path, 1L).ioValue shouldEqual
          Right(metadata)

        acls.fetch(path, self = false).some shouldEqual metadata.map(_ => AccessControlList.empty)
      }
    }
  }
}
