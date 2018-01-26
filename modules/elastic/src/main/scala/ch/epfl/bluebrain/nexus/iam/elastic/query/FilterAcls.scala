package ch.epfl.bluebrain.nexus.iam.elastic.query

import java.util.regex.Pattern.quote

import cats.MonadError
import cats.syntax.functor._
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.http.HttpClient
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path.Empty
import ch.epfl.bluebrain.nexus.commons.iam.acls._
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.types.search._
import ch.epfl.bluebrain.nexus.iam.elastic.ElasticIds._
import ch.epfl.bluebrain.nexus.iam.elastic._
import ch.epfl.bluebrain.nexus.iam.elastic.query.FilterAcls._
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
    * Lists all documents which match the provided ''identity'' and the optionally available filters.
    *
    * @param identities   the identities to match
    * @param path         the path to match.
    *                     It will match a document where the path key is equal to ''pathOpt'' or starts with ''pathOpt''
    * @param pathDepthOpt the optionally available pathDepth to match.
    *                     If exists, It will match a document where the pathDepth key is <= ''pathDepthOpt''
    */
  def apply(identities: Set[Identity],
            path: Path = Empty,
            pathDepthOpt: Option[Int] = None): F[IdentityAccessControlList] = {
    client
      .search[AclDocument](QueryBuilder(path, pathDepthOpt), identities.map(indexId))(pagination, sort = sortByPath)
      .map { qr =>
        val groupedAcl = qr.results.groupBy(_.source.identity)
        IdentityAccessControlList(
          groupedAcl.map {
            case ((identity, docResults)) => SingleIdentityAccessControlList(identity, propagate(docResults, path))
          }.toSet
        )
      }
  }

  private def sortByPath: SortList = SortList(List(Sort("path")))

  private def propagate(qrs: List[QueryResult[AclDocument]], path: Path): List[PathAccessControl] =
    qrs
      .foldLeft((ListMap.empty[Path, (Permissions, Boolean)])) {
        case (acc, current) =>
          val source       = current.source
          val currentPerms = acc.getPreviousParent(source.path) ++ source.permissions
          val store        = source.path.startsWith(path)
          acc + (source.path -> (currentPerms -> store))
      }
      .toList
      .collect {
        case (path, (perms, true)) => PathAccessControl(path, perms)
      }

  private implicit class PermissionsMapSyntax(map: Map[Path, (Permissions, Boolean)]) {
    @tailrec
    final def getPreviousParent(path: Path): Permissions =
      map.get(path) match {
        case Some((permissions, _)) => permissions
        case None                   => if (path.head == Empty) Permissions.empty else getPreviousParent(path.tail)
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

  private val regexPath = "{p}/.*"

  final def apply(path: Path, pathDepthOpt: Option[Int]): Json = {
    val terms = List(pathStartsWithQuery(path), pathDepthOpt.map(pathDepthLteQuery)).flatten
    if (terms.isEmpty)
      Json.obj("query" -> Json.obj("match_all" -> Json.obj()))
    else
      Json.obj(
        "query" -> Json.obj(
          "bool" -> Json.obj("filter" -> Json.obj("bool" -> Json.obj("must" -> Json.arr(terms: _*))))))
  }

  private def pathDepthLteQuery(depth: Int): Json =
    Json.obj("range" -> Json.obj("pathDepth" -> Json.obj("lte" -> Json.fromInt(depth))))

  private def pathStartsWithQuery(path: Path): Option[Json] = {
    @tailrec
    def propagationPaths(p: Path, terms: List[Json] = List.empty): List[Json] = {
      val newTerms = Json.obj("term" -> Json.obj("path" -> Json.fromString(p.toString()))) :: terms
      if (p.head == Empty) newTerms
      else propagationPaths(p.tail, newTerms)
    }
    if (path == Empty) None
    else {
      val regexTerm = Json.obj("regexp" -> Json.obj("path" -> Json.fromString(toRegex(path))))
      Some(Json.obj("bool" -> Json.obj("should" -> Json.arr(regexTerm :: propagationPaths(path): _*))))
    }
  }

  private def toRegex(path: Path): String =
    regexPath.replaceAll(quote("{p}"), path.toString)

}
