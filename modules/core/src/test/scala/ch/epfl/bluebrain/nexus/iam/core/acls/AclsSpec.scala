package ch.epfl.bluebrain.nexus.iam.core.acls

import java.time.Clock
import java.util.UUID

import cats.instances.try_._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.{Anonymous, UserRef}
import ch.epfl.bluebrain.nexus.iam.core.{AuthenticatedUser, User}
import ch.epfl.bluebrain.nexus.iam.core.acls.CallerCtx._
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.State._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.{AccessControlList, Permission, Permissions}
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.{Failure, Success, Try}

class AclsSpec extends WordSpecLike with Matchers {

  private implicit val clock           = Clock.systemUTC
  private implicit val alice: Identity = UserRef("realm", "Alice")
  private implicit val user: User      = AuthenticatedUser(Set(alice))
  private val aggregate                = MemoryAggregate("permission")(Initial, Acls.next, Acls.eval).toF[Try]
  private val acls                     = Acls(aggregate)
  private val Read                     = Permissions(Permission.Read)
  private val Write                    = Permissions(Permission.Write)
  private val Own                      = Permissions(Permission.Own)
  private val OwnRead                  = Permissions(Permission.Own, Permission.Read)
  private val OwnReadWrite             = Permissions(Permission.Own, Permission.Read, Permission.Write)

  "An ACL service" should {

    "not fetch nonexistent permissions" in {
      val path = genPath(genId)
      acls.fetch(path, Anonymous()) shouldEqual Success(None)
    }

    "fetch all created permissions" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous()         -> OwnRead, alice -> OwnReadWrite)) shouldEqual Success(())
      acls.fetch(path) shouldEqual Success(Map(Anonymous() -> OwnRead, alice -> OwnReadWrite))
    }

    "fetch created permissions for a specific identity" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.fetch(path, Anonymous()) shouldEqual Success(Some(OwnRead))
    }

    "prevent adding empty permissions mapping" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList()) shouldEqual Failure(CommandRejected(CannotAddVoidPermissions))
    }

    "prevent adding permissions mapping if it already exists" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> Read, alice -> OwnReadWrite)) shouldEqual Success(())
      acls.add(path, AccessControlList(alice       -> OwnReadWrite)) shouldEqual Failure(
        CommandRejected(CannotAddVoidPermissions))
    }

    "add permissions" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead, alice -> OwnReadWrite)) shouldEqual Success(())
      acls.fetch(path, Anonymous()) shouldEqual Success(Some(OwnRead))
      acls.fetch(path, alice) shouldEqual Success(Some(OwnReadWrite))
    }

    "not add empty set of permissions" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> Permissions.empty)) shouldEqual Failure(
        CommandRejected(CannotAddVoidPermissions))
      acls.add(path, AccessControlList(alice       -> OwnRead)) shouldEqual Success(())
      acls.add(path, AccessControlList(Anonymous() -> Permissions.empty)) shouldEqual Failure(
        CommandRejected(CannotAddVoidPermissions))
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Failure(
        CommandRejected(CannotAddVoidPermissions))
      acls.add(path, AccessControlList(Anonymous() -> Write)) shouldEqual Success(())
      acls.fetch(path, Anonymous()) shouldEqual Success(Some(OwnReadWrite))
      acls.fetch(path, alice) shouldEqual Success(Some(OwnRead))
    }

    "not subtract from nonexistent permissions" in {
      val path = genPath(genId)
      acls.subtract(path, Anonymous(), OwnRead) shouldEqual Failure(
        CommandRejected(CannotSubtractFromNonexistentPermissions))
    }

    "not subtract from nonexistent permissions for a specific identity" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.subtract(path, alice, OwnRead) shouldEqual Failure(CommandRejected(CannotSubtractForNonexistentIdentity))
    }

    "not subtract all permissions for a specific identity" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.subtract(path, Anonymous(), OwnRead) shouldEqual Success(Permissions.empty)
    }

    "subtract permissions" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.subtract(path, Anonymous(), Own) shouldEqual Success(Read)
      acls.fetch(path, Anonymous()) shouldEqual Success(Some(Read))
    }

    "not remove nonexistent permissions" in {
      val path = genPath(genId)
      acls.remove(path, Anonymous()) shouldEqual Failure(CommandRejected(CannotRemoveForNonexistentIdentity))
    }

    "not remove permissions from nonexistent identity" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.remove(path, alice) shouldEqual Failure(CommandRejected(CannotRemoveForNonexistentIdentity))
    }

    "remove permissions" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.remove(path, Anonymous()) shouldEqual Success(())
      acls.fetch(path) shouldEqual Success(Map.empty)
    }

    "clear permissions" in {
      val path = genPath(genId)
      acls.add(path, AccessControlList(alice       -> OwnReadWrite)) shouldEqual Success(())
      acls.add(path, AccessControlList(Anonymous() -> OwnRead)) shouldEqual Success(())
      acls.clear(path) shouldEqual Success(())
      acls.fetch(path) shouldEqual Success(Map.empty)
    }

    "retrieve permissions for nested paths" in {
      val parent = genPath(genId)
      val path   = parent / "a" / "b" / "c"
      acls.add(parent, AccessControlList(Anonymous() -> OwnRead, alice -> OwnRead)) shouldEqual Success(())
      acls.add(path, AccessControlList(alice         -> Write)) shouldEqual Success(())
      acls.retrieve(path, Set(Anonymous(), alice)) shouldEqual Success(
        Map(Anonymous() -> OwnRead, alice -> OwnReadWrite))
      acls.retrieve(path / "d" / "e", Set(Anonymous(), alice)) shouldEqual Success(
        Map(Anonymous() -> OwnRead, alice -> OwnReadWrite))
    }
  }

  private def genId: String       = UUID.randomUUID.toString.toLowerCase
  private def genPath(id: String) = Path(s"/some/$id")

}
