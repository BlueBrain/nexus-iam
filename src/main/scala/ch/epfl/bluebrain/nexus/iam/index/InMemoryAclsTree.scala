package ch.epfl.bluebrain.nexus.iam.index
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

import cats.Id
import ch.epfl.bluebrain.nexus.iam.acls._
import ch.epfl.bluebrain.nexus.iam.index.InMemoryAclsTree._
import ch.epfl.bluebrain.nexus.iam.types.Identity
import ch.epfl.bluebrain.nexus.iam.types.Permission._
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path.Segment
import monix.eval.Task

import scala.annotation.tailrec

/**
  * An in memory implementation of [[AclsIndex]]. It uses a tree structure, stored in the ''tree'' map.
  * Every key on the map is a [[Path]] and its values are a set of children [[Path]]s. In this way one can
  * navigate down the tree.
  *
  * @param tree the data structure used to build the tree with the parent paths and the children paths
  * @param acls a data structure used to store the ACLs for a path
  */
class InMemoryAclsTree private (tree: ConcurrentHashMap[Path, Set[Path]],
                                acls: ConcurrentHashMap[Path, ResourceAccessControlList])
    extends AclsIndex[Id] {

  private val any = "*"

  override def replace(path: Path, aclResource: ResourceAccessControlList): Id[Boolean] = {
    @tailrec
    def inner(p: Path, children: Set[Path]): Unit = {
      tree.merge(p, children, (current, _) => current ++ children)
      if (!(p.isEmpty || p == Path./))
        inner(p.tail(dropSlash = p.tail() != Path./), Set(p))
    }

    val rev = aclResource.rev

    val f: BiFunction[ResourceAccessControlList, ResourceAccessControlList, ResourceAccessControlList] = (curr, _) =>
      curr match {
        case c if rev > c.rev => aclResource
        case other            => other
    }
    val updated = acls.merge(path, aclResource, f)

    val update = updated == aclResource
    if (update) inner(path, Set.empty)
    update
  }

  override def get(path: Path, ancestors: Boolean, self: Boolean)(
      implicit identities: Set[Identity]): Id[AccessControlLists] = {

    def removeNotOwn(currentAcls: AccessControlLists): AccessControlLists = {
      def containsOwn(acl: AccessControlList): Boolean =
        acl.value.exists { case (ident, perms) => identities.contains(ident) && perms.contains(Own) }

      val (_, result) = currentAcls.sorted.value
        .foldLeft(Set.empty[Path] -> AccessControlLists.empty) {
          case ((ownPaths, acc), entry @ (p, _)) if ownPaths.exists(p.startsWith) => ownPaths     -> (acc + entry)
          case ((ownPaths, acc), entry @ (p, acl)) if containsOwn(acl.value)      => ownPaths + p -> (acc + entry)
          case ((ownPaths, acc), (p, acl))                                        => ownPaths     -> (acc + (p -> acl.map(_.filter(identities))))
        }
      result
    }

    if (self) {
      val result = if (ancestors) getWithAncestors(path) else get(path)
      result.filter(identities).removeEmpty
    } else {
      val result = removeNotOwn(getWithAncestors(path))
      if (ancestors)
        result.removeEmpty
      else
        AccessControlLists(result.value.filterKeys(_.size == path.size)).removeEmpty
    }
  }

  private def getWithAncestors(path: Path): AccessControlLists = {
    val currentAcls = get(path)
    if (path.isEmpty || path == Path./) currentAcls
    else currentAcls ++ getWithAncestors(path.tail(dropSlash = path.tail() != Path./))
  }

  private def pathOf(segments: Vector[String]): Path =
    if (segments.isEmpty) Path./ else segments.foldLeft[Path](Path.Empty)(_ / _)

  private def get(path: Path): AccessControlLists = {
    val segments = path.segments.toVector

    def inner(toConsume: Vector[String]): AccessControlLists = {
      if (toConsume.contains(any)) {
        val consumed = toConsume.takeWhile(_ != any)
        tree.getSafe(pathOf(consumed)) match {
          case Some(children) if consumed.size + 1 == segments.size =>
            AccessControlLists(children.foldLeft(Map.empty[Path, ResourceAccessControlList]) { (acc, p) =>
              acls.getSafe(p).map(r => acc + (p -> r)).getOrElse(acc)
            })
          case Some(children) =>
            children.foldLeft(AccessControlLists.empty) {
              case (acc, (Segment(head, _))) =>
                val toConsumeNew = (consumed :+ head) ++ segments.takeRight(segments.size - 1 - consumed.size)
                acc ++ inner(toConsumeNew)
              case (acc, _) => acc
            }
          case None => AccessControlLists.empty
        }
      } else {
        val p = pathOf(toConsume)
        acls.getSafe(p).map(r => AccessControlLists(p -> r)).getOrElse(AccessControlLists.empty)
      }
    }
    inner(segments)
  }

}

object InMemoryAclsTree {

  private[index] implicit class ConcurrentHashMapSyntax[K, V](private val map: ConcurrentHashMap[K, V]) extends AnyVal {
    def getSafe(key: K): Option[V] = Option(map.get(key))
  }

  /**
    * Constructs an in memory implementation of [[AclsIndex]]
    *
    */
  final def apply(): InMemoryAclsTree =
    new InMemoryAclsTree(new ConcurrentHashMap[Path, Set[Path]](),
                         new ConcurrentHashMap[Path, ResourceAccessControlList]())

  /**
    * Constructs an in memory implementation of [[AclsIndex]] using the [[Task]] effect type
    *
    */
  final def task(): AclsIndex[Task] = new AclsIndex[Task] {
    private val underlying = apply()

    override def replace(path: Path, aclResource: ResourceAccessControlList): Task[Boolean] =
      Task.pure(underlying.replace(path, aclResource))

    override def get(path: Path, ancestors: Boolean, self: Boolean)(
        implicit identities: Set[Identity]): Task[AccessControlLists] =
      Task.pure(underlying.get(path, ancestors, self))
  }
}
