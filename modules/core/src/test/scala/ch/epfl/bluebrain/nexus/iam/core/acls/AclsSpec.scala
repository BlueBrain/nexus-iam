package ch.epfl.bluebrain.nexus.iam.core.acls

import java.time.Clock
import java.util.UUID

import akka.http.scaladsl.model.Uri
import cats.instances.try_._
import ch.epfl.bluebrain.nexus.iam.core.acls.CommandRejection._
import ch.epfl.bluebrain.nexus.iam.core.acls.State._
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity
import ch.epfl.bluebrain.nexus.iam.core.identity.Identity._
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.{Failure, Success, Try}

class AclsSpec extends WordSpecLike with Matchers {

  private val aggregate                = MemoryAggregate("permission")(Initial, Acls.next, Acls.eval).toF[Try]
  private val acls                     = Acls(aggregate, Clock.systemUTC)
  private val OwnRead                  = Permissions(Permission.Own, Permission.Read)
  private val OwnReadWrite             = Permissions(Permission.Own, Permission.Read, Permission.Write)
  private implicit val alice: Identity = UserRef(Uri("http://localhost"), "Alice")

  "An ACL service" should {

    "not fetch nonexistent permissions" in {
      val path = genPath(genId)
      acls.fetch(path, Anonymous) shouldEqual Success(None)
    }

    "fetch all created permissions" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.add(path, alice, OwnReadWrite) shouldEqual Success(OwnReadWrite)
      acls.fetch(path) shouldEqual Success(Map(Anonymous -> OwnRead, alice -> OwnReadWrite))
    }

    "fetch created permissions for a specific identity" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.fetch(path, Anonymous) shouldEqual Success(Some(OwnRead))
    }

    "not create empty permissions mapping" in {
      val path = genPath(genId)
      acls.create(path, Map()) shouldEqual Failure(CommandRejected(CannotCreateVoidPermissions))
    }

    "not create permissions mapping if it already exists" in {
      val path = genPath(genId)
      acls.create(path, Map(Anonymous -> OwnRead, alice -> OwnReadWrite)) shouldEqual Success(
        Map(Anonymous                 -> OwnRead, alice -> OwnReadWrite))
      acls.create(path, Map(Anonymous -> OwnRead, alice -> OwnReadWrite)) shouldEqual Failure(
        CommandRejected(CannotCreateExistingPermissions))
    }

    "create permissions" in {
      val path = genPath(genId)
      acls.create(path, Map(Anonymous -> OwnRead, alice -> OwnReadWrite)) shouldEqual Success(
        Map(Anonymous                 -> OwnRead, alice -> OwnReadWrite))
      acls.fetch(path, Anonymous) shouldEqual Success(Some(OwnRead))
      acls.fetch(path, alice) shouldEqual Success(Some(OwnReadWrite))
    }

    "not add empty set of permissions" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, Permissions()) shouldEqual Failure(CommandRejected(CannotAddVoidPermissions))
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.add(path, Anonymous, OwnRead) shouldEqual Failure(CommandRejected(CannotAddVoidPermissions))
    }

    "add permissions" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.fetch(path, Anonymous) shouldEqual Success(Some(OwnRead))
      acls.add(path, Anonymous, Permissions(Permission.Write)) shouldEqual Success(OwnReadWrite)
      acls.fetch(path, Anonymous) shouldEqual Success(Some(OwnReadWrite))
    }

    "not subtract from nonexistent permissions" in {
      val path = genPath(genId)
      acls.subtract(path, Anonymous, OwnRead) shouldEqual Failure(
        CommandRejected(CannotSubtractFromNonexistentPermissions))
    }

    "not subtract from nonexistent permissions for a specific identity" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.subtract(path, alice, OwnRead) shouldEqual Failure(CommandRejected(CannotSubtractForNonexistentIdentity))
    }

    "not subtract all permissions for a specific identity" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.subtract(path, Anonymous, OwnRead) shouldEqual Failure(CommandRejected(CannotSubtractAllPermissions))
    }

    "subtract permissions" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.subtract(path, Anonymous, Permissions(Permission.Own)) shouldEqual Success(
        Some(Permissions(Permission.Read)))
      acls.fetch(path, Anonymous) shouldEqual Success(Some(Permissions(Permission.Read)))
    }

    "not remove nonexistent permissions" in {
      val path = genPath(genId)
      acls.remove(path, Anonymous) shouldEqual Failure(CommandRejected(CannotRemoveForNonexistentIdentity))
    }

    "not remove permissions from nonexistent identity" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.remove(path, alice) shouldEqual Failure(CommandRejected(CannotRemoveForNonexistentIdentity))
    }

    "remove permissions" in {
      val path = genPath(genId)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.remove(path, Anonymous) shouldEqual Success(())
      acls.fetch(path) shouldEqual Success(Map.empty)
    }

    "clear permissions" in {
      val path = genPath(genId)
      acls.add(path, alice, OwnReadWrite) shouldEqual Success(OwnReadWrite)
      acls.add(path, Anonymous, OwnRead) shouldEqual Success(OwnRead)
      acls.clear(path) shouldEqual Success(())
      acls.fetch(path) shouldEqual Success(Map.empty)
    }

  }

  private def genId: String       = UUID.randomUUID.toString.toLowerCase
  private def genPath(id: String) = Path(s"/some/$id")

}
