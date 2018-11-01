package ch.epfl.bluebrain.nexus.iam.index

import cats.Id
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity.UserRef
import ch.epfl.bluebrain.nexus.iam.types.{AccessControlList, Permission}
import ch.epfl.bluebrain.nexus.iam.types.Permission._
import org.openjdk.jmh.annotations._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path._

import scala.util.Random

//noinspection TypeAnnotation
/**
  * Benchmark on Graph operations
  * To run it, execute on the sbt shell: ''jmh:run -i 10 -wi 10 -f1 -t1 .*InMemoryAclsTreeBenchmark.*''
  * Which means "10 iterations" "10 warmup iterations" "1 fork" "1 thread"
  * Results:
  * Benchmark                   Mode  Cnt       Score      Error  Units
  * listBigAclOrgs             thrpt   10   27121,272 ±  501,287  ops/s
  * listBigAclProjectsOnOrg    thrpt   10   52515,036 ± 1729,156  ops/s
  * listBigAllProjects         thrpt   10   31136,426 ± 3893,653  ops/s
  * listSmallAclOrgs           thrpt   10   41252,103 ±  690,302  ops/s
  * listSmallAclProjectsOnOrg  thrpt   10  134938,516 ± 3439,873  ops/s
  * listSmallAllProjects       thrpt   10   51266,234 ±  945,133  ops/s

  */
@State(Scope.Thread)
class InMemoryAclsTreeBenchmark extends Randomness {

  //10 permissions
  val permissions: List[Permission] = Own :: List.fill(9)(Permission(genString(length = 10)).get)

  // Number of ACLs: 1000
  // Number of users <= 100
  // Number of projects <= 100
  // Number of organizations <= 10
  val orgs1                  = List.fill(10)(genString(length = 10))
  val projects1              = List.fill(100)(genString(length = 10))
  val users1: List[Identity] = List.fill(100)(UserRef("realm", genString(length = 10)))

  val index1 = InMemoryAclsTree[Id]()

  ingest(orgs1, projects1, users1, index1, 1000)

  def ingest(orgs: List[String],
             projects: List[String],
             users: List[Identity],
             index: InMemoryAclsTree[Id],
             total: Int) =
    (0 until total).foreach { _ =>
      val org     = orgs(genInt(max = orgs.size - 1))
      val project = projects(genInt(max = projects.size - 1))
      val user    = users(genInt(max = users.size - 1))
      val user2   = users(genInt(max = users.size - 1))
      val perm    = Random.shuffle(permissions).take(4).toSet
      val perm2   = Random.shuffle(permissions).take(4).toSet
      val acl     = AccessControlList(user -> perm, user2 -> perm2)
      genInt(max = 2) match {
        case 0 => index.replace(/, acl)
        case 1 => index.replace(Path(org), acl)
        case 2 => index.replace(org / project, acl)
      }
    }

  @Benchmark
  def listSmallAclOrgs(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users1).take(10).toSet

    val _ = index1.get(Path("*"), ancestors = false, self = false)
  }

  @Benchmark
  def listSmallAclProjectsOnOrg(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users1).take(10).toSet

    val org = orgs1(genInt(max = orgs1.size - 1))
    val _   = index1.get(org / "*", ancestors = false, self = false)
  }

  @Benchmark
  def listSmallAllProjects(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users1).take(10).toSet

    val _ = index1.get("*" / "*", ancestors = false, self = false)
  }

  // Number of ACLs: 100.000
  // Number of users <= 500
  // Number of projects <= 1000
  // Number of organizations <= 100
  val orgs2                  = List.fill(100)(genString(length = 10))
  val projects2              = List.fill(1000)(genString(length = 10))
  val users2: List[Identity] = List.fill(500)(UserRef("realm", genString(length = 10)))

  val index2 = InMemoryAclsTree[Id]()

  ingest(orgs1, projects1, users1, index2, 100000)

  @Benchmark
  def listBigAclOrgs(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users2).take(10).toSet

    val _ = index2.get(Path("*"), ancestors = false, self = false)
  }

  @Benchmark
  def listBigAclProjectsOnOrg(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users2).take(10).toSet

    val org = orgs2(genInt(max = orgs2.size - 1))
    val _   = index2.get(org / "*", ancestors = false, self = false)
  }

  @Benchmark
  def listBigAllProjects(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users2).take(10).toSet

    val _ = index2.get("*" / "*", ancestors = false, self = false)
  }
}
