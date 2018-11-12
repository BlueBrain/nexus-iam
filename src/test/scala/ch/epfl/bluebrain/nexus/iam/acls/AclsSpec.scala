package ch.epfl.bluebrain.nexus.iam.acls

import java.time.{Clock, Instant, ZoneId}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import cats.effect.{ContextShift, IO}
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, GroupRef, UserRef}
import ch.epfl.bluebrain.nexus.iam.IOValues
import ch.epfl.bluebrain.nexus.iam.acls.AclRejection._
import ch.epfl.bluebrain.nexus.iam.acls.Acls._
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.{InitialAcl, InitialIdentities}
import ch.epfl.bluebrain.nexus.iam.config.Vocabulary._
import ch.epfl.bluebrain.nexus.iam.types.{Permission, ResourceMetadata}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

class AclsSpec
    extends TestKit(ActorSystem("AclsSpec"))
    with ScalaFutures
    with WordSpecLike
    with Matchers
    with IOValues
    with Randomness
    with OptionValues
    with EitherValues
    with Inspectors {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(3 second, 100 milliseconds)

  private implicit val ctx: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private implicit val initAcl = InitialAcl(/, InitialIdentities("realm", Set("admin")), Set(writePermission))

  private implicit val clock: Clock        = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private val acls                         = Acls.inMemory[IO].ioValue
  private val identities: List[Identity]   = List(UserRef("realm", "sub"), GroupRef("realm", "group"), Anonymous())
  private val permissions: Set[Permission] = List.fill(300)(Permission(genString(length = 6)).value).toSet
  List(Permission("read").value, Permission("write").value, Permission("other").value, Permission("attach").value)
  private val instant = clock.instant()

  trait Context {
    val createdBy: Identity = UserRef("realm", "sub")
    implicit val tokenIds   = Set(GroupRef("realm", "admin"), createdBy)
    val path: Path          = genString(length = 4) / genString(length = 4)
    val id: AbsoluteIri     = Iri.absolute(s"https://bluebrain.github.io/nexus/acls/").right.value + path.repr
    val user1               = identities(genInt(max = 1))
    val user2               = identities.filterNot(_ == user1).head
    val permsUser1          = Random.shuffle(permissions).take(1 + genInt(max = 299))
    val permsUser2          = Random.shuffle(permissions).take(1 + genInt(max = 299))
    val acl                 = AccessControlList(user1 -> permsUser1, user2 -> permsUser2)
  }

  trait AppendCtx extends Context {
    val permsUser1Append = Random.shuffle(permissions -- permsUser1).take(1 + genInt(max = 299))
    val permsUser2Append = Random.shuffle(permissions -- permsUser2).take(1 + genInt(max = 299))
    val aclAppend        = AccessControlList(user1 -> permsUser1Append, user2 -> permsUser2Append)
  }

  "The Acls surface API" when {

    "performing replace operations" should {

      "reject when subject not present" in new Context {
        acls.replace(path, 0L, acl)(Set(GroupRef("realm", genString()))).ioValue shouldEqual Left(AclMissingSubject)
      }

      "reject when no parent acls/write permissions present" in new Context {
        acls.replace(path, 0L, acl)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when wrong revision" in new Context {
        acls.replace(path, 1L, acl).ioValue shouldEqual Left(AclIncorrectRev(path, 1L))
      }

      "reject when empty permissions" in new Context {
        val emptyAcls = AccessControlList(user1 -> Set.empty, user2 -> permsUser2)
        acls.replace(path, 0L, emptyAcls).ioValue shouldEqual Left(AclInvalidEmptyPermissions(path))
      }

      "successfully be created" in new Context {
        acls.replace(path, 0L, acl).ioValue shouldEqual
          Right(ResourceMetadata(id, 1L, Set(nxv.AccessControlList), createdBy, createdBy, instant, instant))
        acls.fetchUnsafe(path).ioValue shouldEqual acl
      }

      "successfully be updated" in new Context {
        acls.replace(path, 0L, acl).ioValue shouldEqual
          Right(ResourceMetadata(id, 1L, Set(nxv.AccessControlList), createdBy, createdBy, instant, instant))
        val replaced                = AccessControlList(user1 -> permsUser1)
        val updatedBy               = UserRef(genString(), genString())
        val otherIds: Set[Identity] = Set(GroupRef("realm", "admin"), updatedBy)
        acls.replace(path, 1L, replaced)(otherIds).ioValue shouldEqual
          Right(ResourceMetadata(id, 2L, Set(nxv.AccessControlList), createdBy, updatedBy, instant, instant))
        acls.fetchUnsafe(path).ioValue shouldEqual replaced

      }

      "reject when wrong revision after updated" in new Context {
        acls.replace(path, 0L, acl).ioValue shouldEqual
          Right(ResourceMetadata(id, 1L, Set(nxv.AccessControlList), createdBy, createdBy, instant, instant))

        val replaced = AccessControlList(user1 -> permsUser1)
        forAll(List(0L, 2L, 10L)) { rev =>
          acls.replace(path, rev, replaced).ioValue shouldEqual Left(AclIncorrectRev(path, rev))
        }
      }
    }

    "performing append operations" should {

      "reject when trying to append the already existing ACL" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.append(path, 1L, acl).ioValue shouldEqual Left(NothingToBeUpdated(path))
      }

      "reject when trying to append the partially already existing ACL" in new AppendCtx {
        val _      = acls.replace(path, 0L, acl).ioValue.right.value
        val append = AccessControlList(user1 -> permsUser1)
        acls.append(path, 1L, append).ioValue shouldEqual Left(NothingToBeUpdated(path))
      }

      "reject when subject not present" in new AppendCtx {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.append(path, 1L, aclAppend)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when no parent acls/write permissions present" in new AppendCtx {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.append(path, 1L, aclAppend)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when wrong revision" in new AppendCtx {
        acls.replace(path, 0L, acl).ioValue.right.value

        forAll(List(0L, 2L, 10L)) { rev =>
          acls.append(path, rev, aclAppend).ioValue shouldEqual Left(AclIncorrectRev(path, rev))
        }
      }

      "reject when empty permissions" in new AppendCtx {
        acls.replace(path, 0L, acl).ioValue.right.value

        val emptyAcls = AccessControlList(user1 -> Set.empty, user2 -> permsUser2)
        acls.append(path, 1L, emptyAcls).ioValue shouldEqual Left(AclInvalidEmptyPermissions(path))
      }

      "successfully be appended" in new AppendCtx {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.append(path, 1L, aclAppend).ioValue shouldEqual
          Right(ResourceMetadata(id, 2L, Set(nxv.AccessControlList), createdBy, createdBy, instant, instant))

        acls.fetchUnsafe(path).ioValue shouldEqual (aclAppend ++ acl)

      }
    }

    "performing subtract operations" should {

      "reject when trying to subtract nonExisting ACL" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value
        val nonExisting =
          AccessControlList(user1 -> Set(Permission(genString()).value), user2 -> Set(Permission(genString()).value))
        acls.subtract(path, 1L, nonExisting).ioValue shouldEqual Left(NothingToBeUpdated(path))
      }

      "reject when subject not present" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.subtract(path, 1L, acl)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when no parent acls/write permissions present" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.subtract(path, 1L, acl)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when wrong revision" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        forAll(List(0L, 2L, 10L)) { rev =>
          acls.subtract(path, rev, acl).ioValue shouldEqual Left(AclIncorrectRev(path, rev))
        }
      }

      "reject when empty permissions" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        val emptyAcls = AccessControlList(user1 -> Set.empty, user2 -> permsUser2)
        acls.subtract(path, 1L, emptyAcls).ioValue shouldEqual Left(AclInvalidEmptyPermissions(path))
      }

      "successfully be subtracted" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.subtract(path, 1L, acl).ioValue shouldEqual
          Right(ResourceMetadata(id, 2L, Set(nxv.AccessControlList), createdBy, createdBy, instant, instant))

        acls.fetchUnsafe(path).ioValue shouldEqual AccessControlList.empty
      }
    }

    "performing delete operations" should {

      "reject when already deleted" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value
        acls.subtract(path, 1L, acl).ioValue.right.value
        acls.delete(path, 2L).ioValue shouldEqual Left(AclIsEmpty(path))
      }

      "reject when subject not present" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.delete(path, 1L)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when no parent acls/write permissions present" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.delete(path, 1L)(Set(GroupRef(genString(), "admin"), createdBy)).ioValue shouldEqual
          Left(AclUnauthorizedWrite(path))
      }

      "reject when wrong revision" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        forAll(List(0L, 2L, 10L)) { rev =>
          acls.delete(path, rev).ioValue shouldEqual Left(AclIncorrectRev(path, rev))
        }
      }

      "successfully be deleted" in new Context {
        acls.replace(path, 0L, acl).ioValue.right.value

        acls.delete(path, 1L).ioValue shouldEqual
          Right(ResourceMetadata(id, 2L, Set(nxv.AccessControlList), createdBy, createdBy, instant, instant))

        acls.fetchUnsafe(path).ioValue shouldEqual AccessControlList.empty
      }
    }

  }
}
