package ch.epfl.bluebrain.nexus.iam.elastic

import java.util.regex.Pattern.quote

import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cats.{MonadError, Traverse}
import ch.epfl.bluebrain.nexus.commons.es.client.ElasticClient
import ch.epfl.bluebrain.nexus.commons.iam.acls._
import ch.epfl.bluebrain.nexus.commons.iam.acls.Event._
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.SimpleIdentitySerialization._
import ch.epfl.bluebrain.nexus.commons.test.Resources
import ch.epfl.bluebrain.nexus.commons.types.ConcurrentSetBuilder
import ch.epfl.bluebrain.nexus.iam.elastic.ElasticIds._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.java8.time._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json, Printer}
import journal.Logger

/**
  * Event incremental indexing logic that pushes data into an ElasticSearch indexer.
  *
  * @param client the ElasticSearch client to use for communicating with the ElasticSearch indexer
  * @param config the indexing config
  * @tparam F the monadic effect type
  */
class AclIndexer[F[_]](client: ElasticClient[F])(implicit config: ElasticConfig, F: MonadError[F, Throwable])
    extends Resources {

  private val log            = Logger[this.type]
  private val indices        = ConcurrentSetBuilder[String]()
  private lazy val indexJson = jsonContentOf("/elastic-mappings.json", Map(quote("{{type}}") -> config.docType))
  private val printer        = Printer.noSpaces.copy(dropNullValues = true)

  /**
    * Indexes the event by pushing it's json ld representation into the ElasticSearch indexer while also updating the
    * existing content.
    *
    * @param event the event to index
    * @return a Unit value in the ''F[_]'' context
    */
  final def apply(event: Event): F[Unit] = event match {
    case PermissionsAdded(path, acls, m) =>
      log.debug(s"Indexing 'PermissionsAdded' event for path '${path.show}' with acls '$acls'")
      Traverse[List].sequence_(acls.toMap.foldLeft(List(F.pure(()))) {
        case (acc, (identity, perms)) =>
          val index: String = identity
          createIndexIfNotExist(index)
            .flatMap { _ =>
              Traverse[List].sequence_(perms.set.map { perm =>
                val eventUpdate = AclDocument(path, identity, perm, updated = m.instant)
                val eventCreate = eventUpdate.copy(created = Some(m.instant))
                val updateQuery = Json.obj("doc" -> eventUpdate.asJson, "upsert" -> eventCreate.asJson)
                client.update(index, config.docType, id(path, perm), updateQuery)
              }.toList)
            } :: acc
      })

    case PermissionsSubtracted(path, identity, perms, _) =>
      log.debug(
        s"Indexing 'PermissionsSubtracted' event for path '${path.show}' with identity '$identity' and permisions '$perms'")
      val terms = perms.set.foldLeft(List.empty[Json])((acc, perm) => permTerm(perm) :: acc)
      val query = Json.obj(
        "query" -> Json.obj(
          "bool" -> Json.obj("filter" -> Json.obj("bool" -> Json.obj("should" -> Json.arr(terms: _*))))))

      client.deleteDocuments(Set(identity), query)

    case PermissionsRemoved(path, identity, _) =>
      log.debug(s"Indexing 'PermissionsRemoved' event for path '${path.show}' and identity $identity")
      client.deleteDocuments(Set(identity), Json.obj("query" -> pathTerm(path)))

    case PermissionsCleared(path, _) =>
      log.debug(s"Indexing 'PermissionsCleared' event for path '${path.show}'")
      client.deleteDocuments(query = Json.obj("query" -> pathTerm(path)))
  }

  private def pathTerm(path: Path): Json =
    Json.obj("term" -> Json.obj("path" -> Json.fromString(path.show)))

  private def permTerm(perm: Permission): Json =
    Json.obj("term" -> Json.obj("permission" -> Json.fromString(perm.show)))

  private def createIndexIfNotExist(index: String): F[Unit] =
    if (!indices(index))
      client.createIndexIfNotExist(index, indexJson).map(_ => indices += index)
    else
      F.pure(())

  private implicit val encodeRemoveNullKeys: Encoder[AclDocument] = {
    val enc = deriveEncoder[AclDocument]
    Encoder.instance(v => parse(printer.pretty(enc(v))).getOrElse(Json.obj()))
  }

}

object AclIndexer {

  /**
    * Constructs a event incremental indexer that pushes data into an ElasticSearch indexer.
    *
    * @param client the ElasticSearch client to use for communicating with the ElasticSearch indexer
    * @param config the indexing config
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: ElasticClient[F], config: ElasticConfig)(
      implicit F: MonadError[F, Throwable]): AclIndexer[F] = {
    implicit val _ = config
    new AclIndexer[F](client)
  }
}
