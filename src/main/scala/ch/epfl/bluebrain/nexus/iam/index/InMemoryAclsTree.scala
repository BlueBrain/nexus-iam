package ch.epfl.bluebrain.nexus.iam.index
import cats.Applicative
import cats.syntax.applicative._
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.iam.index.InMemoryAclsTree._
import ch.epfl.bluebrain.nexus.iam.types.Permission._
import ch.epfl.bluebrain.nexus.iam.types.{AccessControlList, AccessControlLists}
import ch.epfl.bluebrain.nexus.service.http.Path
import ch.epfl.bluebrain.nexus.service.http.Path.Segment

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * An in memory implementation of [[AclsIndex]]. It uses a tree structure, stored in the ''tree'' map.
  * Every key on the map is a [[Path]] and its values are a set of children [[Path]]s. In this way one can
  * navigate down the tree.
  *
  * Note: This implementation is not Thread safe but can be used safely within an Actor.
  *
  * @param tree the data structure used to build the tree with the parent paths and the children paths
  * @param acls a data structure used to store the ACLs for a path
  * @tparam F the monadic type
  */
class InMemoryAclsTree[F[_]: Applicative] private (tree: mutable.Map[Path, Set[Path]],
                                                   acls: mutable.Map[Path, AccessControlList])
    extends AclsIndex[F] {

  private val any = "*"

  override def replace(path: Path, acl: AccessControlList): F[Unit] = {
    @tailrec
    def inner(p: Path, children: Set[Path], aclOpt: Option[AccessControlList]): Unit = {
      tree.merge(p, children, _ ++ children)
      aclOpt.map(a => acls += (p -> a))
      if (!p.isEmpty) inner(p.tail, Set(p), None)
    }

    inner(path, Set.empty, Some(acl))
    ().pure
  }

  override def get(path: Path, ancestors: Boolean, self: Boolean)(
      implicit identities: Set[Identity]): F[AccessControlLists] = {

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
      result.filter(identities).removeEmpty.pure
    } else {
      val result = removeNotOwn(getWithAncestors(path))
      if (ancestors)
        result.removeEmpty.pure
      else
        AccessControlLists(result.value.filterKeys(_.length == path.length)).removeEmpty.pure
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
        tree.get(pathOf(consumed)) match {
          case Some(children) if consumed.size + 1 == segments.size =>
            AccessControlLists(acls.filterKeys(children.contains).toMap)
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
        acls.get(p).map(acl => AccessControlLists(p -> acl)).getOrElse(AccessControlLists.empty)
      }
    }
    inner(segments)
  }

}

object InMemoryAclsTree {

  /**
    * Constructs an in memory implementation of [[AclsIndex]]
    *
    * @tparam F the monadic type
    */
  final def apply[F[_]: Applicative](): InMemoryAclsTree[F] =
    new InMemoryAclsTree[F](mutable.Map.empty[Path, Set[Path]], mutable.Map.empty[Path, AccessControlList])

  implicit class MapMergeSyntax[K, V](private val map: mutable.Map[K, V]) extends AnyVal {

    /**
      * If the provided ''key'' is not already associated with a value, associates it with the given value.
      * Otherwise, replaces the value with the results of the given ''f'' function. This operation is not atomic.
      *
      * @param key   the key with which the specified value is to be associated
      * @param value the value to use if absent
      * @param f     the function to recompute a value if present
      */
    def merge(key: K, value: V, f: V => V): Unit =
      map.get(key) match {
        case Some(c) => map += (key -> f(c))
        case None    => map += (key -> value)
      }
  }
}
