package ch.epfl.bluebrain.nexus.iam.index

import java.time.{Clock, Instant, ZoneId}

import cats.Id
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.acls.{State => _, _}
import ch.epfl.bluebrain.nexus.iam.config.AppConfig.HttpConfig
import ch.epfl.bluebrain.nexus.iam.types.Identity.User
import ch.epfl.bluebrain.nexus.iam.types.{Identity, Permission, ResourceF}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import org.openjdk.jmh.annotations._
import org.scalatest.EitherValues

import scala.util.Random

/**
  * Benchmark on Graph operations
  * To run it, execute on the sbt shell: ''jmh:run -i 10 -wi 10 -f1 -t1 .*InMemoryAclsTreeBenchmark.*''
  * Which means "10 iterations" "10 warmup iterations" "1 fork" "1 thread"
  * Results:
  * Benchmark                   Mode  Cnt       Score      Error  Units
  * listBigAclOrgs             thrpt   10   30425,510 ±  604,116  ops/s
  * listBigAclProjectsOnOrg    thrpt   10   49143,804 ± 1103,624  ops/s
  * listBigAllProjects         thrpt   10   28490,781 ±  547,737  ops/s
  * listSmallAclOrgs           thrpt   10   51307,592 ±  773,862  ops/s
  * listSmallAclProjectsOnOrg  thrpt   10  136503,019 ± 6442,895  ops/s
  * listSmallAllProjects       thrpt   10   49324,746 ± 1720,131  ops/s
  */
//noinspection TypeAnnotation,NameBooleanParameters
@State(Scope.Thread)
class InMemoryAclsTreeBenchmark extends Randomness with EitherValues {
  private val clock: Clock  = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private implicit val http = HttpConfig("some", 8080, "v1", "http://nexus.example.com")

  val instant = clock.instant()
  //10 permissions
  val permissions: List[Permission] = write :: List.fill(9)(Permission(genString(length = 10)).get)

  // Number of ACLs: 1000
  // Number of users <= 100
  // Number of projects <= 100
  // Number of organizations <= 10
  val orgs1              = List.fill(10)(genString(length = 10))
  val projects1          = List.fill(100)(genString(length = 10))
  val users1: List[User] = List.fill(100)(User(genString(length = 10), "realm"))

  val index1 = InMemoryAclsTree[Id]()

  ingest(orgs1, projects1, users1, index1, 1000)

  def ingest(orgs: List[String],
             projects: List[String],
             users: List[User],
             index: InMemoryAclsTree[Id],
             total: Int): Unit =
    (0 until total).foreach { v =>
      val org     = orgs(genInt(max = orgs.size - 1))
      val project = projects(genInt(max = projects.size - 1))
      val user    = users(genInt(max = users.size - 1))
      val user2   = users(genInt(max = users.size - 1))
      val perm    = Random.shuffle(permissions).take(4).toSet
      val perm2   = Random.shuffle(permissions).take(4).toSet
      val acl = ResourceF(http.aclsIri + "id3",
                          3L,
                          Set.empty,
                          false,
                          instant,
                          user,
                          instant,
                          user2,
                          AccessControlList(user -> perm, user2 -> perm2))
      genInt(max = 2) match {
        case 0 => index.replace(/, acl.copy(rev = v.toLong))
        case 1 => index.replace(Path(org).right.value, acl.copy(rev = v.toLong))
        case 2 => index.replace(org / project, acl.copy(rev = v.toLong))
      }
    }

  @Benchmark
  def listSmallAclOrgs(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users1).take(10).toSet

    val _ = index1.get(Path("*").right.value, ancestors = false, self = false)
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
  val users2: List[Identity] = List.fill(500)(User(genString(length = 10), "realm"))

  val index2 = InMemoryAclsTree()

  ingest(orgs1, projects1, users1, index2, 100000)

  @Benchmark
  def listBigAclOrgs(): Unit = {
    implicit val identities: Set[Identity] = Random.shuffle(users2).take(10).toSet

    val _ = index2.get(Path("*").right.value, ancestors = false, self = false)
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
