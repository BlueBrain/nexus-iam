package ch.epfl.bluebrain.nexus.iam.elastic.query

import java.util.regex.Pattern.quote

import cats.MonadError
import cats.syntax.functor._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.types.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.search._
import ch.epfl.bluebrain.nexus.iam.core.{ServiceAccount, User}
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permission._
import ch.epfl.bluebrain.nexus.iam.core.acls.types.Permissions
import ch.epfl.bluebrain.nexus.iam.elastic.ElasticIds._
import ch.epfl.bluebrain.nexus.iam.elastic._
import ch.epfl.bluebrain.nexus.iam.elastic.query.FilterAcls._
import ch.epfl.bluebrain.nexus.iam.elastic.types.{FullAccessControl, FullAccessControlList}
import ch.epfl.bluebrain.nexus.rdf.Iri.Path
import ch.epfl.bluebrain.nexus.rdf.Iri.Path._
import io.circe.Json

import scala.annotation.tailrec
import scala.collection.immutable.ListMap

/**
  * Queries for aggregating ACLs
  *
  * @param client the ElasticSearch client
  * @tparam F the monadic effect type
  */
class FilterAcls[F[_]](client: ElasticClient[F])(implicit config: ElasticConfig,
                                                 rs: HttpClient[F, QueryResults[AclDocument]],
                                                 F: MonadError[F, Throwable]) {

  /**
    * Lists ACLs based on the provided ''path'', the authenticated identities, and some filtering parameters.
    *
    * @param path    the path to match. It can be '/a/b/c' or it can have the asterisk (*) character to denote a segment of the path with any match
    * @param parents decides whether it should match only the provided ''path'' (false) or the parents also (true)
    * @param self    decides whether it should match only the provided ''identities'' (true) or any identity which has the right own access (true)
    * @param user    the implicitly available user which provides the authenticated identities to match
    */
  def apply(path: Path, parents: Boolean, self: Boolean)(implicit user: User): F[FullAccessControlList] = {
    def search(serviceAccount: Boolean) = {
      val indices: Set[String] = if (self) user.identities.map(indexId) else Set(config.indexPrefix + "_*")
      client
        .search[AclDocument](QueryBuilder(path, parents, self), indices)(pagination, sort = sortByPath)
        .map(queryResultsToAcls(_, path, user.identities, parents, self, serviceAccount))
    }
    user match {
      case ServiceAccount if self => F.pure(FullAccessControlList())
      case ServiceAccount         => search(serviceAccount = true)
      case _                      => search(serviceAccount = false)
    }
  }

  private def sortByPath: SortList = SortList(List(Sort("path")))

  private def queryResultsToAcls(qr: QueryResults[AclDocument],
                                 path: Path,
                                 identities: Set[Identity],
                                 parents: Boolean,
                                 self: Boolean,
                                 serviceAccount: Boolean): FullAccessControlList = {
    val aclList = qr.results
      .foldLeft(ListMap.empty[(Path, Identity), Vector[QueryResult[AclDocument]]]) { (acc, c) =>
        val existing = acc.getOrElse(c.source.path -> c.source.identity, Vector.empty)
        acc + ((c.source.path, c.source.identity) -> (existing :+ c))
      }
      .map {
        case ((p, identity), res) =>
          val permissions =
            res.foldLeft(Permissions.empty)((perms, current) => perms + current.source.permission)
          FullAccessControl(identity, p, permissions)
      }
      .toList
    if (self || serviceAccount) FullAccessControlList(aclList)
    else {
      val filtered = ComputeParents(aclList, identities)
      if (parents)
        FullAccessControlList(filtered)
      else
        FullAccessControlList(filtered.filter { case FullAccessControl(_, p, _) => p.length == path.length })
    }
  }

}

object FilterAcls {

  /**
    * Pagination is not allowed from the client, so we default to maximum pagination
    * supported by ElasticSearch: Note that from + size can not be more than the index.max_result_window
    * index setting which defaults to 10,000
    */
  private val pagination = Pagination(0, 10000)

  /**
    * Constructs a [[FilterAcls]]
    *
    * @param client the ElasticSearch client
    * @param config configurable settings specific to  the ElasticSearch indexer
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: ElasticClient[F], config: ElasticConfig)(
      implicit rs: HttpClient[F, QueryResults[AclDocument]],
      F: MonadError[F, Throwable]): FilterAcls[F] = {
    implicit val _ = config
    new FilterAcls(client)
  }

}

private object QueryBuilder {

  final def apply(path: Path, parents: Boolean, self: Boolean): Json = {

    def queryDepth: Json =
      Json.obj("range" -> Json.obj("pathDepth" -> Json.obj("lte" -> Json.fromInt(path.length))))

    def queryExactTerms: Json = {
      @tailrec
      def buildExactTermPropagation(p: Path, terms: List[Json] = List.empty): List[Json] = {
        val newTerms = Json.obj("term" -> Json.obj("path" -> Json.fromString(p.show))) :: terms
        if (p.head == Empty) newTerms
        else buildExactTermPropagation(p.tail, newTerms)
      }
      if (!parents && self)
        Json.obj("term" -> Json.obj("path" -> Json.fromString(path.show)))
      else
        Json.obj("bool" -> Json.obj("should" -> Json.arr(buildExactTermPropagation(path): _*)))
    }

    def queryRegex: Json = {
      def regex(p: Path): String = p.show.replaceAll(quote("*"), ".*")

      @tailrec
      def buildRegexPropagation(p: Path, accRegex: String = ""): String = {
        val acc = s"$accRegex${regex(p)}"
        if (p.head == Empty) acc
        else buildRegexPropagation(p.tail, s"$acc|")
      }

      val r = if (!parents && self) regex(path) else buildRegexPropagation(path)
      Json.obj("regexp" -> Json.obj("path" -> Json.fromString(r)))
    }

    val pathQuery = if (path.show.contains("*")) queryRegex else queryExactTerms
    val terms     = List(queryDepth, pathQuery)
    Json.obj(
      "query" -> Json.obj("bool" -> Json.obj("filter" -> Json.obj("bool" -> Json.obj("must" -> Json.arr(terms: _*))))))
  }

}

/**
  * Compute the resulting __List[FullAccessControl]__ that contains the ACLs which the provided ''identities'' have access to (the ones with inherited Owm Permissions)
  *
  * @param acl        the list of ACLs from where to compute the accessible ones
  * @param identities the authenticated identities
  */
private class ComputeParents(acl: List[FullAccessControl], identities: Set[Identity]) {

  private val ownPaths: Set[Path] =
    acl.collect {
      case FullAccessControl(identity, path, perms) if identities(identity) && perms.containsAny(Permissions(Own)) =>
        path
    }.toSet

  final def apply(): List[FullAccessControl] =
    groupByPath().foldLeft(List.empty[FullAccessControl]) {
      case (acc, (path, c)) if ownInParent(path) => acc ++ c
      case (acc, (_, c))                         => acc ++ c.filter { case FullAccessControl(identity, _, _) => identities(identity) }
    }

  @tailrec
  private def ownInParent(path: Path): Boolean =
    if (ownPaths(path)) true
    else if (path == Empty) false
    else ownInParent(path.tail)

  private def groupByPath(): ListMap[Path, Vector[FullAccessControl]] =
    acl.foldLeft(ListMap.empty[Path, Vector[FullAccessControl]]) { (acc, c) =>
      acc + (c.path -> (acc.getOrElse(c.path, Vector.empty) :+ c))
    }

}

private object ComputeParents {

  /**
    * Constructs a __List[FullAccessControl]__ with the ACLs that the provided ''identities'' have access to.
    *
    * @param acl        the list of ACLs from where to compute the accessible ones
    * @param identities the authenticated identities
    */
  final def apply(acl: List[FullAccessControl], identities: Set[Identity]): List[FullAccessControl] =
    new ComputeParents(acl, identities).apply()
}
