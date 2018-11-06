package ch.epfl.bluebrain.nexus.iam.index
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

import cats.Id
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.acls.{AccessControlList, AccessControlLists}
import ch.epfl.bluebrain.nexus.iam.index.InMemoryAclsTree._
import ch.epfl.bluebrain.nexus.iam.types.Permission._
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path.Segment

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
                                acls: ConcurrentHashMap[Path, RevAccessControlList])
    extends AclsIndex[Id] {

  private val any = "*"

  override def replace(path: Path, rev: Long, acl: AccessControlList): Id[Boolean] = {
    @tailrec
    def inner(p: Path, children: Set[Path]): Unit = {
      tree.merge(p, children, (current, _) => current ++ children)
      if (!p.isEmpty) inner(p.tail, Set(p))
    }

    val f: BiFunction[RevAccessControlList, RevAccessControlList, RevAccessControlList] = (curr, _) =>
      curr match {
        case (currRev, _) if rev > currRev => rev -> acl
        case other                         => other
    }
    val (updatedRev, updatedAcl) = acls.merge(path, rev -> acl, f)

    val update = updatedRev == rev && updatedAcl == acl
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
          case ((ownPaths, acc), entry @ (p, acl)) if containsOwn(acl)            => ownPaths + p -> (acc + entry)
          case ((ownPaths, acc), (p, acl))                                        => ownPaths     -> (acc + (p -> acl.filter(identities)))
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
        AccessControlLists(result.value.filterKeys(_.length == path.length)).removeEmpty
    }
  }

  private def getWithAncestors(path: Path): AccessControlLists = {
    val currentAcls = get(path)
    if (path.isEmpty) currentAcls
    else currentAcls ++ getWithAncestors(path.tail)
  }

  private def pathOf(segments: Vector[String]): Path = Path(segments.mkString("/", "/", ""))

  private def get(path: Path): AccessControlLists = {
    val segments = path.segments.toVector

    def inner(toConsume: Vector[String]): AccessControlLists = {
      if (toConsume.contains(any)) {
        val consumed = toConsume.takeWhile(_ != any)
        tree.getSafe(pathOf(consumed)) match {
          case Some(children) if consumed.size + 1 == segments.size =>
            AccessControlLists(children.foldLeft(Map.empty[Path, AccessControlList]) { (acc, p) =>
              acls.getSafe(p).map { case (_, acl) => acc + (p -> acl) }.getOrElse(acc)
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
        acls.getSafe(p).map { case (_, acl) => AccessControlLists(p -> acl) }.getOrElse(AccessControlLists.empty)
      }
    }
    inner(segments)
  }

}

object InMemoryAclsTree {

  private[index] type RevAccessControlList = (Long, AccessControlList)

  private[index] implicit class ConcurrentHashMapSyntax[K, V](private val map: ConcurrentHashMap[K, V]) extends AnyVal {
    def getSafe(key: K): Option[V] = Option(map.get(key))
  }

  /**
    * Constructs an in memory implementation of [[AclsIndex]]
    *
    */
  final def apply(): InMemoryAclsTree =
    new InMemoryAclsTree(new ConcurrentHashMap[Path, Set[Path]](), new ConcurrentHashMap[Path, RevAccessControlList]())
}
